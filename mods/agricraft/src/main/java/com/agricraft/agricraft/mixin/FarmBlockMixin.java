package com.agricraft.agricraft.mixin;

import com.agricraft.agricraft.common.block.CropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.19.2 has no {@code minecraft:maintains_farmland} block tag (added in 1.19.4), which upstream AgriCraft adds its
 * crop block to. Farmland under a crop stick therefore keeps its moisture state as it does under a vanilla crop.
 */
@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {

	@Inject(method = "isUnderCrops", at = @At("HEAD"), cancellable = true)
	private static void agricraft$maintainUnderCropSticks(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (level.getBlockState(pos.above()).getBlock() instanceof CropBlock) {
			cir.setReturnValue(true);
		}
	}
}
