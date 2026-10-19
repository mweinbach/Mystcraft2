package net.mystcraft.minecraft.age;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.writing.PageData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgeSymbolCompilerTest {
    private CompoundTag page(String id) { CompoundTag p = new CompoundTag(); PageData.setLegacySymbol(p, id); return p; }
    @Test void compilesSupportedControlsAndPrecedingModernBiomeInOrder() {
        var pages = List.of(page("mystcraft:TerrainFlat"), page("mystcraft:biome/minecraft/desert"),
                page("mystcraft:BioConSingle"), page("mystcraft:WeatherOff"));
        var plan = AgeSymbolCompiler.parse(pages);
        assertEquals("flat", plan.terrain());
        assertEquals(new ResourceLocation("minecraft", "desert"), plan.biome());
        assertEquals("off", plan.weather());
        assertTrue(plan.unsupported().isEmpty());
        assertEquals("mystcraft:TerrainFlat", pages.get(0).getString("symbol"));
    }
    @Test void rejectsReorderedDanglingAndNumericLegacyBiomeParameters() {
        assertFalse(AgeSymbolCompiler.parse(List.of(page("mystcraft:BioConSingle"), page("mystcraft:biome/minecraft/desert"))).unsupported().isEmpty());
        assertFalse(AgeSymbolCompiler.parse(List.of(page("mystcraft:biome/minecraft/desert"))).unsupported().isEmpty());
        assertFalse(AgeSymbolCompiler.parse(List.of(page("mystcraft:Biome2"), page("mystcraft:BioConSingle"))).unsupported().isEmpty());
    }
    @Test void recordsConflictsUnsupportedControlsAndMalformedTagsInsteadOfSilentlyOverwriting() {
        CompoundTag malformed = new CompoundTag(); malformed.putInt("symbol", 4);
        var plan = AgeSymbolCompiler.parse(List.of(page("mystcraft:terrainflat"), page("mystcraft:terrainvoid"),
                page("mystcraft:weatheroff"), page("mystcraft:weatherstorm"), page("mystcraft:lightingdark"), malformed));
        assertEquals("flat", plan.terrain()); assertEquals("off", plan.weather());
        assertEquals(3, plan.unsupported().size());
    }
    @Test void acceptsOneStarFissureButRejectsDuplicateFeatures() {
        var accepted = AgeSymbolCompiler.parse(List.of(page("mystcraft:terrainvoid"), page("mystcraft:starfissure")));
        assertTrue(accepted.starFissure()); assertTrue(accepted.unsupported().isEmpty());
        assertFalse(AgeSymbolCompiler.parse(List.of(page("mystcraft:starfissure"), page("mystcraft:starfissure"))).unsupported().isEmpty());
    }
    @Test void boundsPageCountAndRejectsUnimplementedForcedRain() {
        List<CompoundTag> pages = new ArrayList<>();
        for (int i = 0; i <= AgeSymbolCompiler.MAX_PAGES; i++) pages.add(new CompoundTag());
        assertFalse(AgeSymbolCompiler.parse(pages).unsupported().isEmpty());
        assertEquals("always", AgeSymbolCompiler.parse(List.of(page("mystcraft:weatheron"))).weather());
        assertEquals("slow", AgeSymbolCompiler.parse(List.of(page("mystcraft:weatherslow"))).weather());
        assertEquals("fast", AgeSymbolCompiler.parse(List.of(page("mystcraft:weatherfast"))).weather());
        assertFalse(AgeSymbolCompiler.parse(List.of(page("mystcraft:weatherrain"))).unsupported().isEmpty());
        assertNull(AgeSymbolCompiler.parse(List.of(page("mystcraft:bioconnative"))).biome());
    }
}
