package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.mystcraft.minecraft.age.AgeSymbolCompiler;
import net.mystcraft.minecraft.age.grammar.ClassicGrammar;

/** Real server registry completion/validation without reserving an Age identity or touching book inventory. */
public final class GrammarSmoke {
    private GrammarSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        var bindings = ClassicGrammar.forRegistry(server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY));
        List<CompoundTag> written = new ArrayList<>();
        for (String path : List.of("terrainvoid", "bioconnative", "weatheroff", "lightingnormal", "sundark",
                "moondark", "starsdark", "featurelargedummy", "featuremediumdummy", "featuresmalldummy")) {
            CompoundTag page = new CompoundTag(); page.putString("symbol", "mystcraft:" + path); written.add(page);
        }
        written.get(2).putString("GrammarSmoke", "arbitrary payload retained");
        var before = written.stream().map(CompoundTag::copy).toList();
        var compiled = AgeSymbolCompiler.compile(written, 0, bindings);
        if (!compiled.successful()) throw new AssertionError("Known complete grammar fixture rejected: " + compiled.diagnostics());
        if (!before.equals(written) || compiled.symbolInstability() != 3000 || !compiled.plan().weather().equals("off"))
            throw new AssertionError("Grammar must preserve source pages and apply source dummy costs/weather");
        var again = AgeSymbolCompiler.compile(written, 0, bindings);
        if (!compiled.executionPages().equals(again.executionPages()) || !compiled.symbolSeeds().equals(again.symbolSeeds()))
            throw new AssertionError("Frozen completion must be reproducible");
        CompoundTag fissure = new CompoundTag(); fissure.putString("symbol", "mystcraft:starfissure");
        var repeated = new ArrayList<>(written); repeated.add(fissure); repeated.add(fissure.copy());
        var repeatedResult = AgeSymbolCompiler.compile(repeated, 0, bindings);
        if (!repeatedResult.successful() || !repeatedResult.plan().starFissure()
                || repeatedResult.executionPages().stream().filter(page -> page.getString("symbol").equals("mystcraft:starfissure")).count() < 2)
            throw new AssertionError("Classic duplicate fissure symbols must remain ordered while population success coalesces the feature: " + repeatedResult.diagnostics());
        CompoundTag unavailable = new CompoundTag(); unavailable.putString("symbol", "unavailable_addon:unimplemented_effect"); written.add(unavailable);
        var rejected = AgeSymbolCompiler.compile(written, 0, bindings);
        if (rejected.successful() || rejected.diagnostics().stream().noneMatch(s -> s.contains("unavailable_addon:unimplemented_effect")))
            throw new AssertionError("Registered metadata must not fabricate an unavailable generation handler");
        return List.of("grammar: real registry bindings complete deterministically with preserved original payloads, independent symbol seeds and exact dummy costs",
                "grammar: unknown addon effects remain explicit rejection diagnostics before Age identity allocation");
    }
    public record Audit(int accepted, int rejected, java.util.Map<String, Integer> unsupported,
                        java.util.Map<Long, List<String>> failingSeeds) {
        public Audit { unsupported = java.util.Map.copyOf(unsupported); failingSeeds = java.util.Map.copyOf(failingSeeds); }
    }
    /** Diagnostic audit: rejected seeds are reported honestly, never counted as supported worlds. */
    public static List<String> audit100(MinecraftServer server) {
        var bindings = ClassicGrammar.forRegistry(server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY));
        var simple = new ArrayList<CompoundTag>();
        for (String path : List.of("terrainvoid", "weatheroff")) {
            var page = new CompoundTag(); page.putString("symbol", "mystcraft:" + path); simple.add(page);
        }
        var log = org.slf4j.LoggerFactory.getLogger("MystcraftGrammarAudit");
        for (var input : java.util.Map.of("empty", List.<CompoundTag>of(), "simple_void_weatheroff", simple).entrySet()) {
            Audit result = audit(bindings, input.getValue(), 100);
            log.info("MYSTCRAFT_GRAMMAR_AUDIT {} seeds100 accepted{} rejected{} unsupported{}", input.getKey(),
                    result.accepted(), result.rejected(), new java.util.TreeMap<>(result.unsupported()));
            result.failingSeeds().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(5)
                    .forEach(failure -> log.info("MYSTCRAFT_GRAMMAR_AUDIT_EXAMPLE {} seed{} diagnostics{}",
                            input.getKey(), failure.getKey(), failure.getValue()));
        }
        return List.of("grammar audit: 100 empty and 100 simple seeds evaluated without allocating world identities; exact support distribution logged separately");
    }
    public static Audit audit(ClassicGrammar.DynamicRules bindings, List<CompoundTag> pages, int count) {
        int accepted = 0;
        var distribution = new java.util.TreeMap<String, Integer>();
        var failing = new java.util.LinkedHashMap<Long, List<String>>();
        for (long seed = 0; seed < count; seed++) {
            var compiled = AgeSymbolCompiler.compile(pages, seed, bindings);
            if (compiled.successful()) accepted++;
            else {
                failing.put(seed, compiled.diagnostics());
                new java.util.HashSet<>(compiled.diagnostics()).forEach(problem -> distribution.merge(problem, 1, Integer::sum));
            }
        }
        return new Audit(accepted, count - accepted, distribution, failing);
    }
}
