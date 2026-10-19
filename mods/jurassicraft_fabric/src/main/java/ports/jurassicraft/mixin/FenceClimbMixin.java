package ports.jurassicraft.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ports.jurassicraft.fence.FenceWireBlock;

@Mixin(LivingEntity.class)
public abstract class FenceClimbMixin {
    @Shadow private Optional<BlockPos> lastClimbablePos;

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void jurassicraft$climbFence(CallbackInfoReturnable<Boolean> result) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (result.getReturnValue() || entity.isSpectator()) return;
        BlockPos pos = entity.blockPosition();
        if (entity.level.getBlockState(pos).getBlock() instanceof FenceWireBlock fence && fence.canClimb(entity)) {
            lastClimbablePos = Optional.of(pos);
            result.setReturnValue(true);
        }
    }
}
