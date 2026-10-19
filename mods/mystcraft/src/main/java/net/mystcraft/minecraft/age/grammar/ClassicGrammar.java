package net.mystcraft.minecraft.age.grammar;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Ordered legacy phrasal completion. Completion and runtime availability are deliberately separate. */
public final class ClassicGrammar {
    public static final int VERSION = 1;
    public static final String ROOT = "mystcraft:Age";
    public record Production(String parent, List<String> children, Integer rank) {
        public Production { children = List.copyOf(children); }
    }
    public record Binding(String token, CompoundTag payload, List<Production> productions, boolean afterSymbols) {
        public Binding(String token, CompoundTag payload, List<Production> productions) { this(token, payload, productions, false); }
        public Binding { payload = payload.copy(); productions = List.copyOf(productions); }
        @Override public CompoundTag payload() { return payload.copy(); }
    }
    public record DynamicRules(List<Binding> bindings) {
        public static final DynamicRules EMPTY = new DynamicRules(List.of());
        public DynamicRules { bindings = List.copyOf(bindings); }
    }
    public record Completion(int version, List<CompoundTag> pages, List<Long> symbolSeeds,
                             List<Integer> sourcePositions, List<String> diagnostics) {
        public Completion { pages = copies(pages); symbolSeeds = List.copyOf(symbolSeeds);
            sourcePositions = List.copyOf(sourcePositions); diagnostics = List.copyOf(diagnostics); }
        @Override public List<CompoundTag> pages() { return copies(pages); }
    }
    private ClassicGrammar() {}

    /** Ordered built-in material descriptors and the actual server's registry-ID biome encounter order. */
    public static DynamicRules forRegistry(net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomes) {
        List<Binding> bindings = new ArrayList<>();
        for (var entry : net.mystcraft.minecraft.age.generation.ClassicMaterials.entries()) {
            List<Production> productions = new ArrayList<>();
            String path = entry.block().getPath();
            List<String> order = path.endsWith("_log") ? List.of("Solid", "Organic", "Structure")
                    : path.equals("ice") ? List.of("Solid", "Fluid", "Sea", "Structure", "Crystal")
                    : path.equals("packed_ice") ? List.of("Solid", "Fluid", "Terrain", "Sea", "Structure", "Crystal")
                    : path.equals("obsidian") ? List.of("Solid", "Terrain", "Structure", "Crystal")
                    : path.equals("water") || path.equals("lava") ? List.of("Fluid", "Sea")
                    : entry.crystalRank() > 0 ? List.of("Solid", "Structure", "Crystal")
                    : entry.terrainRank() > 0 ? List.of("Terrain", "Structure", "Solid") : List.of("Solid", "Structure");
            for (String category : order) {
                int rank = switch (category) {
                    case "Terrain" -> entry.terrainRank(); case "Sea" -> entry.seaRank();
                    case "Structure" -> entry.structureRank(); case "Solid" -> entry.solidRank();
                    case "Organic" -> entry.organicRank(); case "Fluid" -> entry.fluidRank();
                    case "Crystal" -> entry.crystalRank(); default -> entry.gasRank();
                };
                addCategory(productions, entry.terminal(), "Block" + category, rank);
            }
            bindings.add(new Binding(entry.terminal(), entry.payload(), productions));
        }
        for (var choice : net.mystcraft.minecraft.writing.DynamicSymbolPages.fluidChoices()) {
            String token = choice.payload().getString("symbol");
            List<Production> productions = new ArrayList<>();
            var nativeFluid = net.minecraft.core.Registry.FLUID.get(new net.minecraft.resources.ResourceLocation(choice.payload().getString("Fluid")));
            Integer grammarRank = net.mystcraft.minecraft.writing.SymbolTuning.fluid(nativeFluid, net.mystcraft.minecraft.writing.DynamicSymbolPages.fluidDisplayName(nativeFluid)).grammarRank();
            if (choice.payload().getBoolean("Sea")) productions.add(new Production("mystcraft:BlockSea", List.of(token), grammarRank));
            productions.add(new Production("mystcraft:BlockFluid", List.of(token), grammarRank));
            bindings.add(new Binding(token, choice.payload(), productions, true));
        }
        for (var biome : biomes) {
            var id = biomes.getKey(biome);
            if (id == null) continue;
            String token = "mystcraft:biome/" + id.getNamespace() + "/" + id.getPath();
            CompoundTag page = new CompoundTag(); page.putString("symbol", token);
            var rule = net.mystcraft.minecraft.writing.SymbolTuning.biome(id); if(rule.blacklisted()) continue;
            Integer rank = rule.grammarRank();
            bindings.add(new Binding(token, page, List.of(new Production("mystcraft:Biome", List.of(token), rank))));
        }
        return new DynamicRules(bindings);
    }
    private static void addCategory(List<Production> productions, String terminal, String category, int rank) {
        if (rank > 0) productions.add(new Production("mystcraft:" + category, List.of(terminal), rank));
    }

    /** No registry/config dependent production is silently invented: callers supply their frozen bindings. */
    public static Completion complete(List<CompoundTag> bookPages, long seed, DynamicRules dynamic) {
        List<String> diagnostics = new ArrayList<>();
        if (bookPages.size() > 256) return new Completion(VERSION, List.of(), List.of(), List.of(), List.of("too_many_pages"));
        GrammarGenerator generator = baseGenerator(dynamic);
        Map<String, CompoundTag> payloads = new LinkedHashMap<>();
        for (var symbol : SymbolRegistry.all()) {
            CompoundTag payload = new CompoundTag(); payload.putString("symbol", symbol.id().toString());
            payloads.put(symbol.legacyId(), payload);
        }
        java.util.Set<String> dynamicTokens = new java.util.HashSet<>();
        for (Binding binding : dynamic.bindings()) {
            if (!dynamicTokens.add(binding.token())) throw new IllegalArgumentException("Duplicate grammar terminal " + binding.token());
            payloads.put(binding.token(), binding.payload());
        }
        List<CompoundTag> originals = new ArrayList<>();
        List<String> terminals = new ArrayList<>();
        for (int i = 0; i < bookPages.size(); i++) {
            CompoundTag page = bookPages.get(i);
            if (page == null) { diagnostics.add("malformed_page:" + i); continue; }
            if (PageData.isLinkPanel(page) || !page.contains("symbol")) continue;
            if (!page.contains("symbol", Tag.TAG_STRING) || PageData.symbol(page).isEmpty()) {
                diagnostics.add("malformed_symbol:" + i); continue;
            }
            String raw = page.getString("symbol");
            String token = SymbolRegistry.find(raw).map(SymbolRegistry.Definition::legacyId).orElse(raw);
            // Modern explicit registry parameters bind to the same grammar category as generated biome pages.
            var lookup = PageData.symbol(page);
            if (lookup.isPresent() && lookup.get().toString().equals("mystcraft:biome") && page.contains("Biome", Tag.TAG_STRING)) {
                var biomeId = net.minecraft.resources.ResourceLocation.tryParse(page.getString("Biome"));
                if (biomeId != null) lookup = java.util.Optional.of(new net.minecraft.resources.ResourceLocation(
                        "mystcraft", "biome/" + biomeId.getNamespace() + "/" + biomeId.getPath()));
            }
            for (Binding binding : dynamic.bindings())
                if (PageData.symbol(binding.payload()).equals(lookup)) { token = binding.token(); break; }
            originals.add(page.copy()); terminals.add(token);
        }
        if (!diagnostics.isEmpty()) return new Completion(VERSION, List.of(), List.of(), List.of(), diagnostics);
        generator.buildGrammar();
        GrammarTree tree = new GrammarTree(generator, ROOT);
        tree.parseTerminals(terminals, new Random(seed));
        List<String> expanded = tree.getExpanded(new Random(seed));
        List<CompoundTag> execution = new ArrayList<>();
        List<Long> symbolSeeds = new ArrayList<>();
        Random logic = new Random(seed);
        for (int i = 0; i < expanded.size(); i++) {
            String terminal = expanded.get(i);
            int source = tree.sourcePositions().get(i);
            CompoundTag payload = source >= 0 ? originals.get(source).copy() : payloads.get(terminal);
            if (payload == null) {
                diagnostics.add("unresolved_grammar_token:" + terminal);
                payload = new CompoundTag(); payload.putString("symbol", terminal);
            }
            execution.add(payload.copy()); symbolSeeds.add(logic.nextLong());
        }
        return new Completion(VERSION, execution, symbolSeeds, tree.sourcePositions(), diagnostics);
    }

    /** Public for pure differential/golden tests against the pinned original engine. */
    public static GrammarGenerator baseGenerator() { return baseGenerator(DynamicRules.EMPTY); }
    private static GrammarGenerator baseGenerator(DynamicRules dynamic) {
        GrammarGenerator generator = new GrammarGenerator();
        try (var input = ClassicGrammar.class.getResourceAsStream("/assets/mystcraft/classic_grammar.tsv")) {
            if (input == null) throw new IllegalStateException("Missing classic grammar productions");
            try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals("# DYNAMIC_BINDINGS")) {
                        for (Binding binding : dynamic.bindings()) if (!binding.afterSymbols()) for (Production production : binding.productions())
                            generator.registerRule(new GrammarGenerator.Rule(production.parent(), production.children(), production.rank()));
                    }
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] fields = line.split("\\t");
                    Integer rank = fields[0].equals("null") ? null : Integer.valueOf(fields[0]);
                    generator.registerRule(new GrammarGenerator.Rule(fields[1],
                            List.of(fields).subList(2, fields.length), rank));
                }
            }
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot load classic grammar", failure); }
        for (Binding binding : dynamic.bindings()) if (binding.afterSymbols()) for (Production production : binding.productions())
            generator.registerRule(new GrammarGenerator.Rule(production.parent(), production.children(), production.rank()));
        return generator;
    }
    private static List<CompoundTag> copies(List<CompoundTag> pages) { return pages.stream().map(CompoundTag::copy).toList(); }
}
