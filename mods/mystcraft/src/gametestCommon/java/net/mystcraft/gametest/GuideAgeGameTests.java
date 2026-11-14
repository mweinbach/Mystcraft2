package net.mystcraft.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.age.AgeSymbolCompiler;
import net.mystcraft.minecraft.age.generation.ClassicGenerationCompiler;
import net.mystcraft.minecraft.age.grammar.ClassicGrammar;
import net.mystcraft.minecraft.writing.PageData;

/** Compile the guide's actual written example in an initialized server registry. */
public final class GuideAgeGameTests {
    private GuideAgeGameTests() {}

    private static CompoundTag page(String symbol) {
        CompoundTag page = new CompoundTag();
        PageData.setLegacySymbol(page, symbol);
        return page;
    }

    private static ClassicGrammar.Binding binding(String token, String... parents) {
        var productions = new ArrayList<ClassicGrammar.Production>();
        for (String parent : parents) productions.add(new ClassicGrammar.Production(
                "mystcraft:" + parent, List.of(token), 1));
        return new ClassicGrammar.Binding(token, page(token), productions);
    }

    public static void compileGuideExample(GameTestHelper helper) {
        CompoundTag panel = new CompoundTag(); panel.put("linkpanel", new CompoundTag());
        var pages = List.of(panel, page("mystcraft:biome/minecraft/plains"), page("mystcraft:BioConSingle"),
                page("mystcraft:ModMat_stone_0"), page("mystcraft:NoSea"), page("mystcraft:TerrainNormal"),
                page("mystcraft:WeatherOff"), page("mystcraft:LightingNormal"), page("mystcraft:SunNormal"),
                page("mystcraft:MoonNormal"), page("mystcraft:StarsNormal"));
        // Fixed vanilla bindings make this independent of the host modpack's registry choices.
        var dynamic = new ClassicGrammar.DynamicRules(List.of(
                binding("mystcraft:biome/minecraft/plains", "Biome"),
                binding("mystcraft:modmat_stone_0", "BlockTerrain", "BlockSolid", "BlockStructure"),
                binding("mystcraft:modmat_water_0", "BlockSea", "BlockFluid")));
        for (long seed : new long[] {1, 42, 9001}) {
            var compilation = AgeSymbolCompiler.compile(pages, seed, dynamic);
            var world = compilation.plan();
            var generation = ClassicGenerationCompiler.compileExecution(compilation.executionPages(), seed);
            if (!world.terrain().equals("normal") || !new ResourceLocation("minecraft:plains").equals(world.biome())
                    || !world.weather().equals("off") || generation.controllerDuplicates() != 0
                    || !new ResourceLocation("minecraft:stone").equals(generation.plan().terrain().fill())
                    || !new ResourceLocation("minecraft:air").equals(generation.plan().terrain().sea()))
                helper.fail("Guide Age controls did not compile as described for seed " + seed);
        }
        helper.succeed();
    }
}
