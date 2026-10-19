package dev.icbm.port.mixin;

import dev.icbm.port.simulation.MovementTrace;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin implements MovementTrace {
  @Unique private @Nullable Vec3d icbm$moveStart;
  @Unique private @Nullable Vec3d icbm$moveEnd;
  @Unique private long icbm$moveTime;

  @Inject(method = "move", at = @At("HEAD"))
  private void icbm$beginStep(MovementType type, Vec3d movement, CallbackInfo info) {
    icbm$moveStart = icbm$self().getPos();
  }

  @Inject(method = "move", at = @At("RETURN"))
  private void icbm$endStep(MovementType type, Vec3d movement, CallbackInfo info) {
    icbm$moveEnd = icbm$self().getPos();
    icbm$moveTime = icbm$self().world.getTime();
  }

  @Override
  public @Nullable Vec3d icbm$stepStart() {
    Entity self = icbm$self();
    return icbm$moveEnd != null
            && icbm$moveEnd.equals(self.getPos())
            && self.world.getTime() - icbm$moveTime <= 1
        ? icbm$moveStart
        : null;
  }

  @Unique
  private Entity icbm$self() {
    return (Entity) (Object) this;
  }
}
