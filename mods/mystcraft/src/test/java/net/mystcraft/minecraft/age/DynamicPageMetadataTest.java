package net.mystcraft.minecraft.age;

import net.minecraft.nbt.CompoundTag;
import net.mystcraft.minecraft.exploration.ExplorationPages;
import net.mystcraft.minecraft.writing.DynamicSymbolPages;
import net.mystcraft.minecraft.writing.SymbolRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicPageMetadataTest {
    @Test void retainsNativeModdedBiomeIdentityInItsFourWordPoemWithoutNumericGuessing() {
        CompoundTag page = new CompoundTag(); page.putString("symbol", "mystcraft:biome/example_worlds/ancient/forest");
        assertTrue(DynamicSymbolPages.isDynamic(page));
        assertEquals(java.util.List.of("Nature", "Nurture", "Encourage", "example_worlds:ancient/forest"), DynamicSymbolPages.poem(page));
        assertTrue(DynamicSymbolPages.displayName(page).getString().contains("example_worlds"));
        CompoundTag numeric = new CompoundTag(); numeric.putString("symbol", "mystcraft:Biome27");
        assertFalse(DynamicSymbolPages.isDynamic(numeric), "old numeric biomes must not be guessed from current registry IDs");
    }
    @Test void materialAndDummyRareCardRanksAreNotClampedToFour() {
        assertEquals(5, ExplorationPages.rank(SymbolRegistry.find("mystcraft:modmat_diamond_ore_0").orElseThrow()));
        assertEquals(5, ExplorationPages.rank(SymbolRegistry.find("mystcraft:featuresmalldummy").orElseThrow()));
        assertEquals(2, ExplorationPages.rank(SymbolRegistry.find("mystcraft:modmat_stone_0").orElseThrow()));
    }
}
