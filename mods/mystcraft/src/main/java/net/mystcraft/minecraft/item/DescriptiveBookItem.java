package net.mystcraft.minecraft.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.minecraft.age.AgeBookData;

public final class DescriptiveBookItem extends LinkingBookItem {
    public DescriptiveBookItem(Properties properties) {
        super(properties, (player, hand, book) -> LinkingNetwork.openBook(player, hand));
    }
    @Override public void appendHoverText(ItemStack book, Level level, List<Component> text, TooltipFlag flag) {
        if (AgeBookData.isUnallocated(book)) text.add(Component.translatable("tooltip.mystcraft.new_age"));
        else super.appendHoverText(book, level, text, flag);
        text.add(Component.translatable("tooltip.mystcraft.page_count", AgeBookData.pages(book).size()));
    }
}
