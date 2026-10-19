package net.mystcraft.minecraft.age.visual;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Random;
import net.mystcraft.minecraft.writing.SymbolRegistry;
import net.minecraft.nbt.CompoundTag;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings.Provider;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings.Rgb;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings.Stop;

/** Shared ordered modifier machine for classic colors, gradients, lighting and explicit celestial pages. */
public final class AgeVisualCompiler {
    public record Result(AgeVisualSettings settings, Set<Integer> consumed, List<String> errors, int danglingInstability, Map<Integer, List<Stop>> lightningGradients) {
        public Result(AgeVisualSettings settings, Set<Integer> consumed, List<String> errors) { this(settings, consumed, errors, 0, Map.of()); }
        public Result(AgeVisualSettings settings, Set<Integer> consumed, List<String> errors, int danglingInstability) { this(settings, consumed, errors, danglingInstability, Map.of()); }
        public Result { lightningGradients = Map.copyOf(lightningGradients); consumed = Set.copyOf(consumed); errors = List.copyOf(errors); }
    }
    private static final Map<String, Rgb> COLORS = Map.ofEntries(
            color("maroon", .5F, 0, 0), color("red", 1, 0, 0), color("olive", .5F, .5F, 0), color("yellow", 1, 1, 0),
            color("darkgreen", 0, .5F, 0), color("green", 0, 1, 0), color("teal", 0, .5F, .5F), color("cyan", 0, 1, 1),
            color("navy", 0, 0, .5F), color("blue", 0, 0, 1), color("purple", .5F, 0, .5F), color("magenta", 1, 0, 1),
            color("black", 0, 0, 0), color("grey", .5F, .5F, .5F), color("silver", .75F, .75F, .75F), color("white", 1, 1, 1));
    private static Map.Entry<String, Rgb> color(String name, float r, float g, float b) { return Map.entry("modcolor" + name, new Rgb(r, g, b)); }
    private AgeVisualCompiler() {}

    private static float averageHeight(Float old, float next) { return old == null ? next : (old + next) / 2; }
    public static Result parse(List<CompoundTag> pages) { return parse(pages, 0); }
    public static Result parse(List<CompoundTag> pages, long ageSeed) {
        return parse(pages, ageSeed, false);
    }
    /** Completed grammar execution: every terminal, including dynamic material/biome tokens, owns a seed. */
    public static Result parseExecution(List<CompoundTag> pages, long ageSeed) { return parse(pages, ageSeed, true); }
    private static Result parse(List<CompoundTag> pages, long ageSeed, boolean execution) {
        Random symbolSeeds = new Random(ageSeed);
        List<AgeCelestialSettings.Body> bodies = new ArrayList<>();
        boolean explicitCelestials = false;
        Float pendingAngle = null, pendingPhase = null;
        List<Integer> anglePages = new ArrayList<>(), phasePages = new ArrayList<>();
        List<Stop> pendingSunset = new ArrayList<>();
        Map<Integer, List<Stop>> lightningGradients = new java.util.HashMap<>();
        Set<Integer> consumed = new HashSet<>(); List<String> errors = new ArrayList<>();
        List<Provider> sky = new ArrayList<>(), fog = new ArrayList<>(), water = new ArrayList<>();
        Rgb pendingColor = null; List<Integer> colorPages = new ArrayList<>();
        Float pendingLength = null; List<Integer> lengthPages = new ArrayList<>();
        List<Stop> gradient = new ArrayList<>(); List<Integer> gradientPages = new ArrayList<>();
        int clearInstability = 0;
        String lighting = "normal", weather = "normal";
        Float cloudHeight = null, horizon = null; boolean drawVoid = true;
        List<Provider> cloud = new ArrayList<>(), grass = new ArrayList<>(), foliage = new ArrayList<>();
        if (pages.size() > (execution ? 8192 : 256)) return new Result(AgeVisualSettings.DEFAULT, Set.of(), List.of("too_many_visual_pages"));
        for (int index = 0; index < pages.size(); index++) {
            CompoundTag page = pages.get(index);
            if (page == null || PageData.isLinkPanel(page)) continue;
            var parsed = PageData.symbol(page);
            if (parsed.isEmpty()) continue;
            long executionSeed = execution ? symbolSeeds.nextLong() : 0;
            if (!parsed.get().getNamespace().equals("mystcraft")) continue;
            String path = parsed.get().getPath();
            // Bounded approximation: advance for every known EXPLICIT symbol, not only bodies.
            // The completed/randomly-filled legacy grammar stream is not present in this port.
            long symbolSeed = execution ? executionSeed : SymbolRegistry.find(page.getString("symbol")).isPresent() ? symbolSeeds.nextLong() : 0;
            if (path.equals("modclear")) {
                int outstanding = (pendingColor == null ? 0 : 1) + (pendingLength == null ? 0 : 1)
                        + (pendingAngle == null ? 0 : 1) + (pendingPhase == null ? 0 : 1) + (gradientPages.isEmpty() ? 0 : 1);
                clearInstability += outstanding * 20;
                consumed.addAll(colorPages); consumed.addAll(lengthPages); consumed.addAll(anglePages); consumed.addAll(phasePages); consumed.addAll(gradientPages);
                pendingColor = null; pendingLength = null; pendingAngle = null; pendingPhase = null;
                colorPages.clear(); lengthPages.clear(); anglePages.clear(); phasePages.clear(); gradient.clear(); gradientPages.clear(); pendingSunset.clear();
                consumed.add(index); continue;
            }
            switch (path) {
                case "terrainnether" -> { cloudHeight = averageHeight(cloudHeight, 200); horizon = averageHeight(horizon, 128); }
                case "terrainvoid" -> { cloudHeight = averageHeight(cloudHeight, 0); horizon = averageHeight(horizon, 0); drawVoid = false; }
                case "terrainend", "starsendsky" -> { horizon = averageHeight(horizon, 0); drawVoid = false; }
                case "nohorizon" -> { horizon = averageHeight(horizon, 0); drawVoid = false; consumed.add(index); }
                case "skylands" -> { cloudHeight = averageHeight(cloudHeight, 42.5F); horizon = averageHeight(horizon, 0); }
            }
            String weatherMode = switch (path) {
                case "weathernorm" -> "normal"; case "weatheroff" -> "off"; case "weatheron" -> "always";
                case "weatherslow" -> "slow"; case "weatherfast" -> "fast"; case "weatherrain" -> "rain";
                case "weathersnow" -> "snow"; case "weatherstorm" -> "storm"; case "weathercloudy" -> "cloudy"; default -> null;
            };
            if (weatherMode != null) weather = weatherMode;
            Float angle = switch(path) { case "modnorth" -> 0F; case "modeast" -> 90F; case "modsouth" -> 180F; case "modwest" -> 270F; default -> null; };
            if (angle != null) { pendingAngle = pendingAngle == null ? angle : CelestialMath.averageAngles(pendingAngle, angle); anglePages.add(index); continue; }
            Float phase = switch(path) { case "modend" -> 0F; case "modrising" -> 90F; case "modnoon" -> 180F; case "modsetting" -> 270F; default -> null; };
            if (phase != null) { pendingPhase = pendingPhase == null ? phase : CelestialMath.averageAngles(pendingPhase, phase); phasePages.add(index); continue; }
            Rgb color = COLORS.get(path);
            if (color != null) {
                pendingColor = pendingColor == null ? color : pendingColor.average(color);
                colorPages.add(index); continue;
            }
            Float length = switch (path) { case "modzero" -> 0F; case "modhalf" -> .5F; case "modfull" -> 1F; case "moddouble" -> 2F; default -> null; };
            if (length != null) {
                pendingLength = pendingLength == null ? length : (pendingLength + length) / 2;
                lengthPages.add(index); continue;
            }
            if (path.equals("modgradient")) {
                // Classic pops the length even if no color is available; an empty gradient is not a usable effect.
                if (pendingColor == null) { if (!execution) errors.add("gradient_without_color_at:" + index); }
                else gradient.add(new Stop(pendingColor, pendingLength == null || pendingLength <= 0 ? 1 : pendingLength));
                gradientPages.addAll(colorPages); gradientPages.addAll(lengthPages); gradientPages.add(index);
                pendingColor = null; colorPages.clear(); pendingLength = null; lengthPages.clear();
                continue;
            }
            if (path.equals("envlightning")) {
                List<Stop> lightning;
                if (!gradient.isEmpty()) {
                    lightning = List.copyOf(gradient); consumed.addAll(gradientPages); gradient.clear(); gradientPages.clear();
                } else {
                    lightning = pendingColor == null ? List.of() : List.of(new Stop(pendingColor, 1));
                    consumed.addAll(colorPages); consumed.addAll(gradientPages); gradientPages.clear(); pendingColor = null; colorPages.clear();
                }
                lightningGradients.put(index, lightning);
                // The runtime owns the terminal, not this visual helper; the consumed modifiers are shared.
                continue;
            }
            if (path.equals("colorhorizon")) {
                if (!gradient.isEmpty()) {
                    pendingSunset.addAll(gradient); consumed.addAll(gradientPages); gradient.clear(); gradientPages.clear();
                } else {
                    if (pendingColor != null) pendingSunset.add(new Stop(pendingColor, 1));
                    consumed.addAll(colorPages); consumed.addAll(gradientPages); gradientPages.clear();
                    pendingColor = null; colorPages.clear();
                }
                consumed.add(index); continue;
            }
            if (List.of("sundark", "moondark", "starsdark").contains(path)) {
                explicitCelestials = true; consumed.add(index); continue;
            }
            if (path.equals("rainbow")) {
                explicitCelestials = true;
                bodies.add(AgeCelestialCompiler.realize(AgeCelestialSettings.Kind.RAINBOW, symbolSeed, null, pendingAngle, null, List.of(), List.of()));
                consumed.addAll(anglePages); anglePages.clear(); pendingAngle = null;
                consumed.add(index); continue;
            }
            AgeCelestialSettings.Kind kind = switch(path) {
                case "sunnormal" -> AgeCelestialSettings.Kind.SUN;
                case "moonnormal" -> AgeCelestialSettings.Kind.MOON;
                case "starsnormal" -> AgeCelestialSettings.Kind.STARS;
                case "starstwinkle" -> AgeCelestialSettings.Kind.TWINKLE;
                case "starsendsky" -> AgeCelestialSettings.Kind.END_SKY;
                default -> null;
            };
            if (kind != null) {
                explicitCelestials = true;
                boolean orbit = kind == AgeCelestialSettings.Kind.SUN || kind == AgeCelestialSettings.Kind.MOON;
                boolean end = kind == AgeCelestialSettings.Kind.END_SKY;
                List<Stop> bodyGradient = List.of(), bodySunset = List.of();
                if (orbit) { bodySunset = List.copyOf(pendingSunset); pendingSunset.clear(); }
                else if (!gradient.isEmpty()) {
                    bodyGradient = List.copyOf(gradient); consumed.addAll(gradientPages); gradient.clear(); gradientPages.clear();
                } else {
                    Rgb fallback = end ? new Rgb(.156F, .156F, .156F) : new Rgb(1, 1, 1);
                    bodyGradient = List.of(new Stop(pendingColor == null ? fallback : pendingColor, 1));
                    consumed.addAll(colorPages); consumed.addAll(gradientPages); gradientPages.clear(); pendingColor = null; colorPages.clear();
                }
                bodies.add(AgeCelestialCompiler.realize(kind, symbolSeed, end ? null : pendingLength, end ? null : pendingAngle,
                        orbit ? pendingPhase : null, bodyGradient, bodySunset));
                if (!end) {
                    consumed.addAll(lengthPages); pendingLength = null; lengthPages.clear();
                    consumed.addAll(anglePages); pendingAngle = null; anglePages.clear();
                }
                if (orbit) { consumed.addAll(phasePages); pendingPhase = null; phasePages.clear(); }
                consumed.add(index); continue;
            }
            if (path.startsWith("lighting") && List.of("lightingnormal", "lightingdark", "lightingbright").contains(path)) {
                consumed.add(index);
                lighting = path.substring("lighting".length());
                continue;
            }
            if (List.of("colorsky", "colorskynight", "colorfog", "colorcloud").contains(path)) {
                List<Stop> stops;
                if (!gradient.isEmpty()) {
                    stops = List.copyOf(gradient); consumed.addAll(gradientPages); gradient.clear(); gradientPages.clear();
                    // A nonempty gradient deliberately does not consume a separate pending color, as in popGradient.
                } else {
                    Rgb fallback = path.equals("colorfog") ? new Rgb(.7529412F, .8470588F, 1) : new Rgb(1, 1, 1);
                    stops = List.of(new Stop(pendingColor == null ? fallback : pendingColor, 1));
                    consumed.addAll(colorPages); consumed.addAll(gradientPages); gradientPages.clear();
                    pendingColor = null; colorPages.clear();
                }
                if (path.equals("colorcloud")) cloud.add(new Provider("gradient", stops));
                else if (path.equals("colorfog")) fog.add(new Provider("gradient", stops));
                else sky.add(new Provider(path.equals("colorskynight") ? "night" : "day", stops));
                consumed.add(index); continue;
            }
            if (path.equals("colorgrass") || path.equals("colorfoliage")) {
                consumed.add(index);
                if (pendingColor == null) errors.add("static_color_without_color:" + index);
                else (path.equals("colorgrass") ? grass : foliage).add(new Provider("constant", List.of(new Stop(pendingColor, 1))));
                consumed.addAll(colorPages); pendingColor = null; colorPages.clear(); continue;
            }
            if (List.of("colorcloudnat", "colorgrassnat", "colorfoliagenat").contains(path)) {
                (path.equals("colorcloudnat") ? cloud : path.equals("colorgrassnat") ? grass : foliage).add(new Provider("natural", List.of()));
                consumed.add(index); continue;
            }
            if (path.equals("colorwater")) {
                consumed.add(index);
                if (pendingColor == null) errors.add("water_color_without_color:" + index);
                else water.add(new Provider("constant", List.of(new Stop(pendingColor, 1))));
                consumed.addAll(colorPages); pendingColor = null; colorPages.clear(); continue;
            }
            if (List.of("colorskynat", "colorfognat", "colorwaternat").contains(path)) {
                (path.equals("colorskynat") ? sky : path.equals("colorfognat") ? fog : water).add(new Provider("natural", List.of()));
                consumed.add(index);
            }
        }
        // Unconsumed modifiers remain visible to the enclosing compiler, which rejects them rather than discarding intent.
        if (!execution && !explicitCelestials && !pendingSunset.isEmpty()) errors.add("horizon_requires_explicit_celestial");
        AgeCelestialSettings celestials = explicitCelestials ? new AgeCelestialSettings(false, bodies, pendingSunset) : AgeCelestialSettings.LEGACY;
        int dangling = clearInstability;
        if (execution) {
            dangling += 100 * ((pendingColor == null ? 0 : 1) + (pendingLength == null ? 0 : 1)
                    + (pendingAngle == null ? 0 : 1) + (pendingPhase == null ? 0 : 1) + (gradientPages.isEmpty() ? 0 : 1));
            consumed.addAll(colorPages); consumed.addAll(lengthPages); consumed.addAll(anglePages); consumed.addAll(phasePages); consumed.addAll(gradientPages);
        }
        return new Result(new AgeVisualSettings(lighting, sky, fog, water, celestials, new AgeVisualSettings.Environment(cloud, grass, foliage, cloudHeight == null ? 128 : cloudHeight, horizon == null ? 63 : horizon, weather, drawVoid)), consumed, errors, dangling, lightningGradients);
    }
}
