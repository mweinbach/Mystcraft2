package com.agricraft.agricraft.common.forge.block.entity;

import com.agricraft.agricraft.common.block.entity.CropBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forge crop block entity. Upstream exposes Botania's horn-harvest capability here; that integration
 * returns with the compat phase.
 */
public class ForgeCropBlockEntity extends CropBlockEntity {

	public ForgeCropBlockEntity(BlockPos blockPos, BlockState blockState) {
		super(blockPos, blockState);
	}

}
