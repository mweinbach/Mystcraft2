package com.agricraft.agricraft.common.item;

import com.agricraft.agricraft.common.block.irrigation.IrrigationChannelBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Irrigation channel valve, ported from AgriCraft 3's {@code ItemChannelValve} (MIT): use it on a channel to fit a valve.
 */
public class ChannelValveItem extends Item {

	public ChannelValveItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof IrrigationChannelBlock channel && !state.getValue(IrrigationChannelBlock.VALVE).hasValve()) {
			if (!level.isClientSide) {
				// a valve fitted to a powered hollow channel starts closed
				boolean closed = channel.isHollow() && level.hasNeighborSignal(pos);
				level.setBlock(pos, state.setValue(IrrigationChannelBlock.VALVE, closed ? IrrigationChannelBlock.Valve.CLOSED : IrrigationChannelBlock.Valve.OPEN), Block.UPDATE_ALL);
				IrrigationChannelBlock.playValveSound(level, pos);
				Player player = context.getPlayer();
				if (player == null || !player.getAbilities().instabuild) {
					context.getItemInHand().shrink(1);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return super.useOn(context);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("agricraft.tooltip.valve.l1").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("agricraft.tooltip.valve.l2").withStyle(ChatFormatting.DARK_GRAY));
		tooltip.add(Component.translatable("agricraft.tooltip.valve.l3").withStyle(ChatFormatting.DARK_GRAY));
	}

}
