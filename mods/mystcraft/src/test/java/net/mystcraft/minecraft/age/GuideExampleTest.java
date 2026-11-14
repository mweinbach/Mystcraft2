package net.mystcraft.minecraft.age;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.mystcraft.minecraft.age.grammar.ClassicGrammar;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideExampleTest {
    private static CompoundTag page(String symbol) {
        CompoundTag result = new CompoundTag();
        PageData.setLegacySymbol(result, symbol);
        return result;
    }

    private static ClassicGrammar.Binding binding(String token, String... parents) {
        var productions = new ArrayList<ClassicGrammar.Production>();
        for (String parent : parents) productions.add(new ClassicGrammar.Production(
                "mystcraft:" + parent, List.of(token), 1));
        return new ClassicGrammar.Binding(token, page(token), productions);
    }

    private static String name(String id) {
        return SymbolRegistry.find("mystcraft:" + id).orElseThrow().displayName();
    }

    @Test void guidedPlainsAgeKeepsItsAuthoredWorldControlsAfterGrammarCompletion() {
        CompoundTag panel = new CompoundTag(); panel.put("linkpanel", new CompoundTag());
        var written = List.of(panel, page("mystcraft:biome/minecraft/plains"), page("mystcraft:BioConSingle"),
                page("mystcraft:ModMat_stone_0"), page("mystcraft:NoSea"), page("mystcraft:TerrainNormal"),
                page("mystcraft:WeatherOff"), page("mystcraft:LightingNormal"), page("mystcraft:SunNormal"),
                page("mystcraft:MoonNormal"), page("mystcraft:StarsNormal"));
        // These bindings correspond to the vanilla pages in a server's dynamic registry.
        var dynamic = new ClassicGrammar.DynamicRules(List.of(
                binding("mystcraft:biome/minecraft/plains", "Biome"),
                binding("mystcraft:modmat_stone_0", "BlockTerrain", "BlockSolid", "BlockStructure"),
                binding("mystcraft:modmat_water_0", "BlockSea", "BlockFluid")));

        for (long seed : new long[] {1, 42, 9001}) {
            var completion = ClassicGrammar.complete(written, seed, dynamic);
            assertTrue(completion.diagnostics().isEmpty(), completion.diagnostics().toString());
            var symbols = completion.pages().stream().map(page -> PageData.symbol(page).orElseThrow().toString()).toList();
            // Completion may fill unrelated features, but must not add competing controllers.
            assertEquals(List.of("mystcraft:bioconsingle"), symbols.stream().filter(s -> s.startsWith("mystcraft:biocon")).toList());
            assertEquals(List.of("mystcraft:terrainnormal"), symbols.stream().filter(s -> s.startsWith("mystcraft:terrain")).toList());
            assertEquals(List.of("mystcraft:weatheroff"), symbols.stream().filter(s -> s.startsWith("mystcraft:weather")).toList());
            assertEquals(List.of("mystcraft:lightingnormal"), symbols.stream().filter(s -> s.startsWith("mystcraft:lighting")).toList());
            int biome = symbols.indexOf("mystcraft:biome/minecraft/plains");
            assertEquals("mystcraft:bioconsingle", symbols.get(biome + 1));
            int stone = symbols.indexOf("mystcraft:modmat_stone_0");
            assertEquals(List.of("mystcraft:modmat_stone_0", "mystcraft:nosea", "mystcraft:terrainnormal"),
                    symbols.subList(stone, stone + 3), "seed=" + seed);
        }
    }

    @Test void displayedExampleMatchesThePagesValidatedAbove() {
        try (var input = new InputStreamReader(getClass().getResourceAsStream(
                "/assets/mystcraft/lang/en_us.json"), StandardCharsets.UTF_8)) {
            var language = JsonParser.parseReader(input).getAsJsonObject();
            var labels = new ArrayList<String>();
            var numberedLine = Pattern.compile("(?m)^\\d+\\. ([^\\n]+)$");
            for (int page = 13; page <= 15; page++) {
                var lines = numberedLine.matcher(language.get("book.mystcraft.guide.page." + page).getAsString());
                while (lines.find()) labels.add(lines.group(1));
            }
            assertEquals(List.of(language.get("item.mystcraft.linking_panel").getAsString(), "Plains",
                    name("BioConSingle"), name("ModMat_stone_0"), name("NoSea"), name("TerrainNormal"),
                    name("WeatherOff"), name("LightingNormal"), name("SunNormal"), name("MoonNormal"),
                    name("StarsNormal")), labels);
        } catch (java.io.IOException failure) {
            fail(failure);
        }
    }
}
