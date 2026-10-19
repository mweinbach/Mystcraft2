package net.mystcraft.minecraft.age;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.mystcraft.minecraft.age.grammar.ClassicGrammar;
import net.mystcraft.minecraft.age.grammar.GrammarTree;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassicGrammarTest {
    private static final List<List<String>> INPUTS = List.of(List.of(),
            List.of("TerrainVoid","BioConNative","WeatherOff","LightingNormal","SunDark","MoonDark","StarsDark",
                    "FeatureLargeDummy","FeatureMediumDummy","FeatureSmallDummy"),
            List.of("ModNorth","WeatherOff","WeatherOn","TerrainFlat"));

    /** Captured by compiling the pinned, UNMODIFIED original engine with only Minecraft/logger stubs. */
    @Test void matchesTwelveIndependentLegacyGoldenExpansionsIncludingSeedResetAndDuplicateControllers() throws Exception {
        try (var reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(
                "/mystcraft/classic-grammar-golden.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] expected = line.split("\t", 4);
                int fixture = Integer.parseInt(expected[0]); long seed = Long.parseLong(expected[1]);
                var generator = ClassicGrammar.baseGenerator(); generator.buildGrammar();
                var tree = new GrammarTree(generator, ClassicGrammar.ROOT);
                var terminals = INPUTS.get(fixture).stream().map(s -> "mystcraft:" + s).toList();
                tree.parseTerminals(terminals, new Random(seed));
                List<String> actual = tree.getExpanded(new Random(seed));
                assertEquals(expected[3], String.join("|", actual), "fixture=" + fixture + " seed=" + seed);
                assertEquals(expected[2], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(String.join("|", actual).getBytes(StandardCharsets.UTF_8))));
                int nextOriginal = 0;
                for (int i = 0; i < actual.size(); i++) if (tree.sourcePositions().get(i) >= 0) {
                    assertEquals(nextOriginal, tree.sourcePositions().get(i));
                    assertEquals(terminals.get(nextOriginal++), actual.get(i));
                }
                assertEquals(terminals.size(), nextOriginal, "all written symbols remain ordered, including unattached ones");
            }
        }
    }

    @Test void preservesUnknownPayloadsAndSeparatesWrittenPagesFromGeneratedSymbolsAndSeeds() {
        CompoundTag panel = new CompoundTag(); panel.put("linkpanel", new CompoundTag());
        CompoundTag first = new CompoundTag(); first.putString("symbol", "mystcraft:WeatherOff"); first.putInt("Quality", 17);
        CompoundTag unknown = new CompoundTag(); unknown.putString("Marker", "retained"); first.put("Addon", unknown);
        CompoundTag second = first.copy(); second.putInt("Quality", 23);
        var originals = List.of(panel, first, second);
        var before = originals.stream().map(CompoundTag::copy).toList();
        var result = ClassicGrammar.complete(originals, 42, ClassicGrammar.DynamicRules.EMPTY);
        assertEquals(before, originals);
        assertEquals(result.pages().size(), result.symbolSeeds().size());
        Random seeds = new Random(42);
        int written = 0;
        for (int i = 0; i < result.pages().size(); i++) {
            assertEquals(seeds.nextLong(), result.symbolSeeds().get(i));
            int source = result.sourcePositions().get(i);
            if (source >= 0) { assertEquals(before.get(source + 1), result.pages().get(i)); written++; }
        }
        assertEquals(2, written);
        result.pages().get(0).putString("Mutation", "external");
        assertFalse(result.pages().get(0).contains("Mutation"));
    }
    @Test void classicRepeatedFissureIsRetainedAndCoalescedBySourcePopulationSuccessFlag() {
        CompoundTag first = new CompoundTag(); first.putString("symbol", "mystcraft:starfissure");
        var input = java.util.List.of(first, first.copy());
        var completion = ClassicGrammar.complete(input, 17, ClassicGrammar.DynamicRules.EMPTY);
        assertTrue(completion.pages().stream().filter(page -> page.getString("symbol").equals("mystcraft:starfissure")).count() >= 2);
        // Source AgeController.populate short-circuits later IPopulate callbacks once one succeeds.
        assertEquals(0, AgeSymbolCompiler.sourceSymbolCost("starfissure", 1));
        assertEquals(0, AgeSymbolCompiler.sourceSymbolCost("starfissure", 2));
        assertTrue(AgeSymbolCompiler.parse(input).unsupported().stream().anyMatch(s -> s.startsWith("duplicate_star_fissure:")),
                "pre-grammar saved-plan validation remains unchanged");
    }
}
