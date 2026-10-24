package com.agricraft.agricraft.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Block item with fixed tooltip lines, used for the irrigation blocks (AgriCraft 3's {@code ItemIrrigationTank} and
 * {@code ItemSprinkler} tooltips).
 */
public class TooltipBlockItem extends BlockItem {

	private final String[] tooltipKeys;

	public TooltipBlockItem(Block block, Properties properties, String... tooltipKeys) {
		super(block, properties);
		this.tooltipKeys = tooltipKeys;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		for (String key : this.tooltipKeys) {
			tooltip.add(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

}
