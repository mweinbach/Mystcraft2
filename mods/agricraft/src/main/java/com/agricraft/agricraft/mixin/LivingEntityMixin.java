package com.agricraft.agricraft.mixin;

import com.agricraft.agricraft.common.block.irrigation.IrrigationTankBlock;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Irrigation tanks with a ladder attached are climbable (AgriCraft 3 used Forge's {@code isLadder}; a state-dependent
 * ladder cannot be expressed with the vanilla climbable tag).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
	private void agricraft$climbTankLadder(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			return;
		}
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity.isSpectator()) {
			return;
		}
		BlockState state = entity.getFeetBlockState();
		if (state.getBlock() instanceof IrrigationTankBlock && state.getValue(IrrigationTankBlock.LADDER)) {
			cir.setReturnValue(true);
		}
	}

}
