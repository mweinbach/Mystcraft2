package net.mystcraft.minecraft.writing;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PageDataTest {
    @Test void retainsOrderedDuplicateBlankAndUnknownPagePayloadsWithoutAliasing() {
        CompoundTag source = new CompoundTag();
        PageData.setLegacySymbol(source, "old addon:Not a modern Identifier");
        CompoundTag nested = new CompoundTag(); nested.putInt("quality", 7); source.put("Addon", nested);
        CompoundTag book = new CompoundTag();
        PageData.writePages(book, List.of(source, new CompoundTag(), source));
        nested.putInt("quality", 99);
        List<CompoundTag> pages = PageData.readPages(book);
        assertEquals(3, pages.size());
        assertEquals("old addon:Not a modern Identifier", PageData.legacySymbol(pages.get(0)).orElseThrow());
        assertTrue(PageData.symbol(pages.get(0)).isEmpty());
        assertTrue(pages.get(1).isEmpty());
        assertEquals(pages.get(0), pages.get(2));
        assertEquals(7, pages.get(0).getCompound("Addon").getInt("quality"));
        pages.get(0).getCompound("Addon").putInt("quality", -1);
        assertEquals(7, PageData.readPages(book).get(0).getCompound("Addon").getInt("quality"));
        assertEquals(7, pages.get(2).getCompound("Addon").getInt("quality"));
    }
    @Test void identifierViewDoesNotRewriteLegacyCaseOrUnknownKeys() {
        CompoundTag page = new CompoundTag(); PageData.setLegacySymbol(page, "mystcraft:TerrainFlat");
        assertEquals(new ResourceLocation("mystcraft", "terrainflat"), PageData.symbol(page).orElseThrow());
        assertEquals("mystcraft:TerrainFlat", page.getString("symbol"));
        PageData.makeLinkPanel(page);
        assertTrue(PageData.isLinkPanel(page));
        assertEquals("mystcraft:TerrainFlat", page.getString("symbol"));
    }
}
