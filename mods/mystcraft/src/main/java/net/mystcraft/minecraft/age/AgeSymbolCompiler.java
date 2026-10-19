package net.mystcraft.minecraft.age;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.writing.PageData;

/** Legacy saved-plan parser and versioned classic completion entry point for new allocations. */
public final class AgeSymbolCompiler {
    public static final int MAX_PAGES = 256;
    private static final Set<String> TERRAINS = Set.of("normal", "flat", "void", "nether", "end", "amplified");
    public record Plan(String terrain, ResourceLocation biome, String weather, List<String> unsupported, boolean starFissure) {
        public Plan { unsupported = List.copyOf(unsupported); }
        public Plan(String terrain, ResourceLocation biome, String weather, List<String> unsupported) {
            this(terrain, biome, weather, unsupported, false);
        }
    }
    public record Compilation(int version, Plan plan, List<CompoundTag> executionPages,
                              List<Long> symbolSeeds, List<String> diagnostics, int symbolInstability,
                              java.util.Map<String, Integer> explicitEffects) {
        public Compilation { executionPages = executionPages.stream().map(CompoundTag::copy).toList();
            symbolSeeds = List.copyOf(symbolSeeds); diagnostics = List.copyOf(diagnostics);
            explicitEffects = java.util.Map.copyOf(explicitEffects); }
        @Override public List<CompoundTag> executionPages() { return executionPages.stream().map(CompoundTag::copy).toList(); }
        public boolean successful() { return diagnostics.isEmpty(); }
    }
    /** New allocation entry point; original book payloads are never changed. */
    public static Compilation compile(List<CompoundTag> bookPages, long seed,
            net.mystcraft.minecraft.age.grammar.ClassicGrammar.DynamicRules dynamicRules) {
        var completion = net.mystcraft.minecraft.age.grammar.ClassicGrammar.complete(bookPages, seed, dynamicRules);
        Plan plan = parse(completion.pages(), seed, true);
        List<String> diagnostics = new ArrayList<>(completion.diagnostics()); diagnostics.addAll(plan.unsupported());
        var generation = net.mystcraft.minecraft.age.generation.ClassicGenerationCompiler.compileExecution(completion.pages(), seed);
        var visual = net.mystcraft.minecraft.age.visual.AgeVisualCompiler.parseExecution(completion.pages(), seed);
        int cost = generation.danglingInstability() + visual.danglingInstability();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>(), controllers = new java.util.HashMap<>();
        java.util.Map<String, Integer> effects = new java.util.LinkedHashMap<>();
        for (CompoundTag page : completion.pages()) {
            var id = PageData.symbol(page); if (id.isEmpty() || !id.get().getNamespace().equals("mystcraft")) continue;
            String path = id.get().getPath(); int count = counts.merge(path, 1, Integer::sum);
            cost += sourceSymbolCost(path, count);
            String controller = path.startsWith("terrain") && TERRAINS.contains(path.substring(7)) ? "terrain"
                    : path.startsWith("biocon") ? "biome" : path.startsWith("weather") ? "weather"
                    : path.startsWith("lighting") ? "lighting" : null;
            if (controller != null && controllers.merge(controller, 1, Integer::sum) > 1) cost += 500;
            String effect = switch (path) { case "envaccel" -> "accelerated"; case "envexplosions" -> "explosions";
                case "envlightning" -> "lightning"; case "envmeteor" -> "meteors"; case "envscorch" -> "burning"; default -> null; };
            if (effect != null) effects.merge(effect, 1, Integer::sum);
        }
        return new Compilation(completion.version(), plan, completion.pages(), completion.symbolSeeds(), diagnostics, cost, effects);
    }
    /** Source SymbolBase defaults to zero; these are all built-in overriding classes at the pinned commit. */
    public static int sourceSymbolCost(String path, int count) {
        return switch (path) {
            case "lightingbright" -> 500; case "envaccel" -> 1000;
            case "envexplosions" -> -500; case "envmeteor" -> -1000;
            case "envscorch", "envlightning" -> count == 1 ? -500 : 0;
            case "featuremediumdummy" -> 1000; case "featuresmalldummy" -> 2000;
            case "netherfort", "mineshafts", "strongholds", "villages" -> count > 3 ? 100 : 0;
            default -> 0;
        };
    }
    private AgeSymbolCompiler() {}

    public static Plan parse(List<CompoundTag> pages) { return parse(pages, 0, false); }
    public static Plan parseExecution(List<CompoundTag> pages, long seed) { return parse(pages, seed, true); }
    private static Plan parse(List<CompoundTag> pages, long seed, boolean classic) {
        List<String> unsupported = new ArrayList<>();
        String terrain = "normal", weather = "normal";
        ResourceLocation biome = null, pendingBiome = null;
        boolean hasTerrain = false, hasWeather = false, hasBiomeController = false, starFissure = false;
        java.util.Set<String> normalDefaults = new java.util.HashSet<>();
        if (pages.size() > (classic ? 8192 : MAX_PAGES)) {
            return new Plan(terrain, biome, weather, List.of("too_many_pages:" + pages.size()));
        }
        var visual = classic ? net.mystcraft.minecraft.age.visual.AgeVisualCompiler.parseExecution(pages, seed)
                : net.mystcraft.minecraft.age.visual.AgeVisualCompiler.parse(pages);
        unsupported.addAll(visual.errors());
        var generation = classic ? net.mystcraft.minecraft.age.generation.ClassicGenerationCompiler.compileExecution(pages, seed) : null;
        if (classic) {
            unsupported.addAll(generation.errors());
            terrain = generation.plan().terrain().terrain();
            biome = generation.plan().biomeController().equals("single") && !generation.plan().biomes().isEmpty()
                    ? generation.plan().biomes().get(0) : null;
            weather = visual.settings().environment().weather();
        }
        for (int i = 0; i < pages.size(); i++) {
            if (classic && generation.consumed().contains(i)) continue;
            if (visual.consumed().contains(i)) {
                if (pendingBiome != null) { unsupported.add("dangling_biome_before_page:" + i); pendingBiome = null; }
                continue;
            }
            CompoundTag page = pages.get(i);
            if (page == null) { unsupported.add("malformed_page:" + i); continue; }
            if (!page.contains("symbol")) {
                if (pendingBiome != null) { unsupported.add("dangling_biome_before_page:" + i); pendingBiome = null; }
                continue;
            }
            if (!page.contains("symbol", Tag.TAG_STRING) || page.getString("symbol").isBlank()) {
                unsupported.add("malformed_symbol:" + i); continue;
            }
            String raw = page.getString("symbol");
            var parsed = PageData.identifier(raw);
            if (parsed.isEmpty() || PageData.isLinkPanel(page)) {
                unsupported.add("malformed_symbol:" + raw); continue;
            }
            ResourceLocation id = parsed.get();
            String path = id.getPath();
            boolean single = id.getNamespace().equals("mystcraft") && path.equals("bioconsingle");
            if (pendingBiome != null && !single) {
                unsupported.add("dangling_biome_before:" + raw);
                pendingBiome = null;
            }
            if (id.getNamespace().equals("mystcraft") && (path.equals("biome") || path.startsWith("biome/"))) {
                ResourceLocation selected = modernBiome(page, path);
                if (selected == null) unsupported.add("malformed_biome:" + raw);
                else pendingBiome = selected;
                continue;
            }
            if (!id.getNamespace().equals("mystcraft")) { unsupported.add(raw); continue; }
            if (path.startsWith("terrain") && TERRAINS.contains(path.substring(7))) {
                if (hasTerrain && !classic) unsupported.add("conflicting_terrain:" + raw);
                else { terrain = path.substring(7); hasTerrain = true; }
            } else if (single || path.equals("bioconnative")) {
                if (hasBiomeController) unsupported.add("conflicting_biome_controller:" + raw);
                else if (single && pendingBiome == null) unsupported.add("missing_biome_parameter:" + raw);
                else { biome = single ? pendingBiome : null; hasBiomeController = true; }
                pendingBiome = null;
            } else if (path.equals("weathernorm") || path.equals("weatheroff") || path.equals("weatheron")
                    || path.equals("weatherslow") || path.equals("weatherfast")
                    || classic && Set.of("weatherrain", "weathersnow", "weatherstorm", "weathercloudy").contains(path)) {
                if (hasWeather && !classic) unsupported.add("conflicting_weather:" + raw);
                else {
                    weather = switch (path) {
                        case "weatheroff" -> "off";
                        case "weatheron" -> "always";
                        case "weatherslow" -> "slow";
                        case "weatherfast" -> "fast";
                        case "weatherrain" -> "rain"; case "weathersnow" -> "snow";
                        case "weatherstorm" -> "storm"; case "weathercloudy" -> "cloudy";
                        default -> "normal";
                    };
                    hasWeather = true;
                }
            } else if (classic && (path.equals("pvpoff") || path.equals("featurelargedummy")
                    || path.equals("featuremediumdummy") || path.equals("featuresmalldummy")
                    || Set.of("envaccel", "envexplosions", "envlightning", "envmeteor", "envscorch").contains(path))) {
                // Source SymbolDummy and AntiPvP have no generation handler. Costs/PvP are frozen separately.
            } else if (path.equals("starfissure")) {
                if (starFissure && !classic) unsupported.add("duplicate_star_fissure:" + raw);
                else starFissure = true;
            } else if (path.equals("lightingnormal") || path.equals("sunnormal")
                    || path.equals("moonnormal") || path.equals("starsnormal")) {
                // Explicit normal defaults only. Repeating celestial controls requests extra bodies.
                if (!normalDefaults.add(path)) unsupported.add("duplicate_default_control:" + raw);
            } else unsupported.add(raw);
        }
        if (pendingBiome != null) unsupported.add("dangling_biome:" + pendingBiome);
        return new Plan(terrain, biome, weather, unsupported, starFissure);
    }

    private static ResourceLocation modernBiome(CompoundTag page, String symbolPath) {
        if (symbolPath.equals("biome")) {
            if (!page.contains("Biome", Tag.TAG_STRING)) return null;
            return ResourceLocation.tryParse(page.getString("Biome"));
        }
        String[] parts = symbolPath.substring("biome/".length()).split("/", 2);
        return parts.length == 2 ? ResourceLocation.tryParse(parts[0] + ":" + parts[1]) : null;
    }
}
