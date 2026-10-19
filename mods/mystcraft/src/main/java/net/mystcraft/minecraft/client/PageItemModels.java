package net.mystcraft.minecraft.client;

import net.minecraft.world.item.ItemStack;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Register this value as the mystcraft:page item model predicate on the page item. */
public final class PageItemModels {
    private PageItemModels() {}
    public static float value(ItemStack page) {
        if (PageData.isLinkPanel(page)) return 2.0F / 256.0F;
        if (PageData.isBlank(page)) return 0;
        return PageData.legacySymbol(page).flatMap(SymbolRegistry::find)
                .map(definition -> (float) definition.modelValue() / 256.0F).orElse(1.0F / 256.0F);
    }
}
