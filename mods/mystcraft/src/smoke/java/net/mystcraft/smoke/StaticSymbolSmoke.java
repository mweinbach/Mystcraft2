package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Registry;
import net.minecraft.server.MinecraftServer;
import net.mystcraft.minecraft.age.AgeAllocation;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.age.generation.ClassicGenerationCompiler;
import net.mystcraft.minecraft.age.generation.ClassicGenerationPlan;
import net.mystcraft.minecraft.age.generation.ClassicMaterials;
import net.mystcraft.minecraft.age.visual.AgeVisualCompiler;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Exhaustive fixed-catalog acceptance with valid modifier consumers, no canonical Age reservation/world creation. */
public final class StaticSymbolSmoke {
    private static final long[] SEEDS = {0, 1, -1, 0x123456789abcdefL};
    private StaticSymbolSmoke() {}
    private static final java.util.Map<MinecraftServer, Session> PENDING = new java.util.HashMap<>();
    public static void begin(MinecraftServer server, java.util.function.Consumer<List<String>> complete,
                             java.util.function.Consumer<Throwable> failed) {
        check(server.isSameThread(), "symbol audit starts on server thread");
        if (PENDING.containsKey(server)) throw new IllegalStateException("A static symbol audit is already running");
        PENDING.put(server, new Session(server, complete, failed));
    }
    public static void tick(MinecraftServer server) {
        Session session = PENDING.get(server);
        if (session == null) return;
        try {
            // One complete catalog target per tick, retaining all four witnesses atomically.
            // No worker accesses registries/worlds, and no blocking loop spans the catalog.
            if (session.step()) {
                PENDING.remove(server);
                session.complete.accept(session.finish());
            }
        } catch (Throwable failure) {
            PENDING.remove(server);
            session.failed.accept(failure);
        }
    }
    public static void stop(MinecraftServer server) { PENDING.remove(server); }
    private static final class Session {
        final MinecraftServer server;
        final java.util.function.Consumer<List<String>> complete;
        final java.util.function.Consumer<Throwable> failed;
        final int ages, worlds;
        final List<SymbolRegistry.Definition> definitions;
        int index, witnesses, queueChecks;
        final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("MystcraftStaticSymbolAudit");
        Session(MinecraftServer server, java.util.function.Consumer<List<String>> complete,
                java.util.function.Consumer<Throwable> failed) {
            check(server.isSameThread(), "static symbol audit must run on server thread");
            this.server=server;this.complete=complete;this.failed=failed;
            ages=AgeManager.data(server).entries().size();worlds=server.levelKeys().size();
            definitions=List.copyOf(SymbolRegistry.all());
            check(definitions.size()==145,"fixed catalog must contain exactly145 unique symbols");
        }
        boolean step() {
            check(server.isSameThread(), "static symbol audit steps must run on server thread");
            var definition=definitions.get(index);
            String path = definition.id().getPath();
            var context = context(path);
            int marked = -1;
            for (int i = 0; i < context.size(); i++) if (path.equals(PageData.symbol(context.get(i)).orElseThrow().getPath())) { marked = i; break; }
            check(marked >= 0, "context must include target " + path);
            context.get(marked).putString("StaticSymbolAudit", path);
            var written = new ArrayList<CompoundTag>();
            // A known complete root skeleton avoids treating a random repair of a malformed partial book
            // as the target symbol's contract. Target usage comes last, preserving source last-controller wins.
            add(written, "terrainvoid", "bioconnative", "weatheroff", "lightingnormal", "sunnormal", "moonnormal", "starsnormal",
                    "featurelargedummy", "featuremediumdummy", "featuresmalldummy");
            written.addAll(context);
            var before = written.stream().map(CompoundTag::copy).toList();
            var generation = ClassicGenerationCompiler.compileExecution(context, SEEDS[0]);
            var visuals = AgeVisualCompiler.parseExecution(context, SEEDS[0]);
            check(generation.errors().isEmpty() && visuals.errors().isEmpty(), "source-valid modifier queue rejected " + path + ":" + generation.errors() + visuals.errors());
            if (queueOwned(path)) {
                check(generation.consumed().contains(marked) || visuals.consumed().contains(marked), "modifier/consumer target was silently ignored " + path);
                queueChecks++;
            }
            if (path.startsWith("modmat_")) {
                var material = ClassicMaterials.find("mystcraft:" + path);
                var selected = material.seaRank() > 0 ? generation.plan().sea() : material.terrainRank() > 0
                        ? generation.plan().fill() : generation.plan().alterations().get(0).state();
                check(Registry.BLOCK.getKey(selected.getBlock()).equals(material.block()), "material category queue did not reach its actual source consumer " + path);
            }
            for (long seed : SEEDS) {
                var prepared = AgeAllocation.compile(server, written, seed);
                var compiled = prepared.compilation();
                check(compiled.successful(), "catalog " + path + " seed " + seed + " rejected:" + compiled.diagnostics());
                var execution = compiled.executionPages();
                check(execution.stream().anyMatch(page -> page.getString("StaticSymbolAudit").equals(path)), "completion lost authored target payload " + path);
                check(compiled.symbolSeeds().size() == execution.size(), "one seed is required per completed terminal " + path);
                Random expected = new Random(seed);
                for (long actual : compiled.symbolSeeds()) check(actual == expected.nextLong(), "third independent source RNG stream drift " + path);
                var state = prepared.state();
                var visual = AgeVisualSettings.fromTag(state.getCompound(AgeVisualSettings.STATE_KEY));
                check(visual.equals(AgeVisualSettings.fromTag(visual.toTag())), "frozen visual profile failed roundtrip " + path);
                check(state.contains(ClassicGenerationPlan.KEY, 10) && state.contains(AgeAllocation.EXECUTION, 10)
                        && state.getInt("ClassicRuntimeVersion") == 1, "allocation skipped executable frozen state " + path);
                var frozenGeneration = ClassicGenerationPlan.fromTag(state.getCompound(ClassicGenerationPlan.KEY));
                check(frozenGeneration.toTag().equals(state.getCompound(ClassicGenerationPlan.KEY)), "frozen generation plan failed NBT roundtrip " + path);
                frozenGeneration.fill(); frozenGeneration.sea();
                var frozen = PageData.readPages(state.getCompound(AgeAllocation.EXECUTION));
                check(frozen.equals(execution), "frozen execution differs from validated terminals " + path);
                check(written.equals(before), "completion/allocation mutated caller-owned pages " + path);
                if (seed == SEEDS[0]) {
                    var repeat = AgeAllocation.compile(server, written, seed);
                    check(prepared.state().equals(repeat.state()) && compiled.executionPages().equals(repeat.compilation().executionPages()), "same-seed frozen allocation is nondeterministic " + path);
                }
                witnesses++;
            }
            log.info("MYSTCRAFT_STATIC_SYMBOL_PASS {} seeds{}", definition.id(), SEEDS.length);
            index++;
            return index == definitions.size();
        }
        List<String> finish() {
        check(AgeManager.data(server).entries().size() == ages && server.levelKeys().size() == worlds, "isolated symbol audit reserved a canonical Age or loaded a world");
        return List.of("static symbols: " + definitions.size() + "/145 catalog terminals accepted in " + witnesses + " source-valid completion/allocation witnesses across4 deterministic seeds; every marked authored payload preserved",
                "static symbols: " + queueChecks + " modifier/controller/feature queue targets consumed; all145 repeat deterministically with frozen generation/visual/execution roundtrips and exact independent symbol RNG streams",
                "static symbols: exhaustive allocation preparation creates zero canonical Age identities or dimensions and does not mutate authored input");
    }
    }
    /** Pair modifiers with their source categories; never demand a standalone malformed modifier book succeed. */
    private static List<CompoundTag> context(String path) {
        var pages = new ArrayList<CompoundTag>();
        if (path.startsWith("modmat_")) {
            var material = ClassicMaterials.find("mystcraft:" + path);
            check(material != null, "missing material descriptor " + path);
            add(pages, path, material.seaRank() > 0 || material.terrainRank() > 0 ? "terrainflat" : "termodspheres");
        } else if (path.equals("nosea")) add(pages, path, "terrainflat");
        else if (path.startsWith("modcolor")) add(pages, path, "colorwater");
        else if (List.of("modnorth", "modeast", "modsouth", "modwest").contains(path)) add(pages, path, "rainbow");
        else if (List.of("modend", "modrising", "modnoon", "modsetting").contains(path)) add(pages, "modfull", "modnorth", path, "sunnormal");
        else if (List.of("modzero", "modhalf", "modfull", "moddouble").contains(path)) add(pages, path, "modnorth", "modnoon", "sunnormal");
        else if (path.equals("modgradient")) add(pages, "modcolorred", path, "colorcloud");
        else if (path.equals("modclear")) add(pages, "modcolorred", "modfull", path);
        else if (path.equals("colorhorizon")) add(pages, "modcolorred", path, "sunnormal");
        else if (path.startsWith("color") && !path.endsWith("nat")) add(pages, "modcolorred", path);
        else if (path.startsWith("biocon") && !path.equals("bioconnative")) add(pages, "biome/minecraft/plains", "biome/minecraft/forest", path);
        else add(pages, path);
        return pages;
    }
    private static boolean queueOwned(String path) {
        return path.startsWith("mod") || path.startsWith("color") || path.startsWith("biocon") || path.startsWith("terrain")
                || path.startsWith("lighting") || List.of("nosea", "rainbow", "nohorizon", "caves", "ravines", "dungeons", "floatislands", "hugetrees", "lakesdeep", "lakessurface", "mineshafts", "netherfort", "obelisks", "termodspheres", "genspikes", "strongholds", "tendrils", "villages", "cryform", "skylands", "denseores", "sundark", "sunnormal", "moondark", "moonnormal", "starsdark", "starsnormal", "starstwinkle", "starsendsky").contains(path);
    }
    private static void add(List<CompoundTag> pages, String... paths) {
        for (String path : paths) { var page = new CompoundTag(); page.putString("symbol", "mystcraft:" + path); pages.add(page); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
