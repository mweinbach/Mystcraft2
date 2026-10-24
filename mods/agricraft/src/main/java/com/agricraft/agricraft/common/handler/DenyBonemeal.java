package com.agricraft.agricraft.common.handler;

import com.agricraft.agricraft.api.AgriApi;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class DenyBonemeal {

	public static boolean denyBonemeal(Player player, InteractionHand hand, BlockPos pos, Level level) {
		if (!player.isShiftKeyDown()) {
			return false;
		}
		ItemStack heldItem = player.getItemInHand(hand);
		if (!heldItem.isEmpty() && heldItem.is(Items.BONE_MEAL)) {
			return AgriApi.getCrop(level, pos).isPresent();
		}
		return false;
	}

}
