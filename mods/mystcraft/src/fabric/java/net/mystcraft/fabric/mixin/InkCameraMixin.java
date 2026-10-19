package net.mystcraft.fabric.mixin;
import net.minecraft.client.Camera;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.mystcraft.integration.registry.InkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** Under-fluid camera behavior is local to rendering; InkFogMixin supplies the original ink color. */
@Mixin(Camera.class)
public abstract class InkCameraMixin {
    @WrapOperation(method="getFluidInCamera",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean mystcraft$inkCamera(FluidState state,TagKey<Fluid> tag,Operation<Boolean> original){return original.call(state,tag)||tag==FluidTags.WATER&&state.getType().isSame(InkRegistry.BLACK_INK.get());}
}
