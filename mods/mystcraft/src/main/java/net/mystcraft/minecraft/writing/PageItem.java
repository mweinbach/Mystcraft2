package net.mystcraft.minecraft.writing;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.mystcraft.integration.MystcraftItems;

/** Blank and symbol pages share an item; metadata does not imply working world-generation logic. */
public final class PageItem extends Item {
    public PageItem(Properties properties) { super(properties); }

    @Override public void fillItemCategory(CreativeModeTab category, NonNullList<ItemStack> items) {
        if (category != CreativeModeTab.TAB_SEARCH && category != MystcraftItems.TAB) return;
        items.add(new ItemStack(this));
        if (MystcraftItems.LINKING_PANEL != null && this == MystcraftItems.LINKING_PANEL.get()) return;
        for (SymbolRegistry.Definition symbol : SymbolRegistry.all()) {
            ItemStack page = new ItemStack(this);
            PageData.setLegacySymbol(page, symbol.legacyId());
            items.add(page);
        }
        for (var choice : DynamicSymbolPages.choices(DynamicSymbolPages.clientRegistries())) {
            ItemStack page = new ItemStack(this); PageData.applyPayload(page, choice.payload()); items.add(page);
        }
    }
    @Override public Component getName(ItemStack stack) {
        if (MystcraftItems.LINKING_PANEL != null && this == MystcraftItems.LINKING_PANEL.get()) return super.getName(stack);
        if (DynamicSymbolPages.isDynamic(stack)) return Component.literal("Symbol Page (").append(DynamicSymbolPages.displayName(stack)).append(")");
        return PageData.legacySymbol(stack).map(id -> Component.literal("Symbol Page (" +
                SymbolRegistry.find(id).map(SymbolRegistry.Definition::displayName).orElse("Unknown: " + id) + ")"))
                .orElseGet(() -> Component.literal(PageData.isLinkPanel(stack) ? "Linking Panel" : "Blank Page"));
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        for (String property : PageData.linkProperties(stack)) tooltip.add(Component.literal(property).withStyle(ChatFormatting.GREEN));
        PageData.legacySymbol(stack).ifPresent(id -> {
            if (flag.isAdvanced()) tooltip.add(Component.literal(id).withStyle(ChatFormatting.GRAY));
            if (!DynamicSymbolPages.isDynamic(stack) && SymbolRegistry.find(id).isEmpty())
                tooltip.add(Component.literal("Unknown symbol").withStyle(ChatFormatting.YELLOW));
        });
    }
}
