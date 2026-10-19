package net.mystcraft.fabric.mixin;
import java.util.stream.Stream;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.mystcraft.integration.registry.InkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
/** Material-water entity behavior without assigning ink global water chemistry or farmland rules. */
@Mixin(Entity.class)
public abstract class InkEntityPhysicsMixin {
    @WrapOperation(method={"updateFluidHeightAndDoFluidPushing","updateSwimming"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean mystcraft$inkBody(FluidState state,TagKey<Fluid> tag,Operation<Boolean> original){return original.call(state,tag)||tag==FluidTags.WATER&&state.getType().isSame(InkRegistry.BLACK_INK.get());}
    @WrapOperation(method="updateFluidOnEyes",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;getTags()Ljava/util/stream/Stream;"))
    private Stream<TagKey<Fluid>> mystcraft$inkEyes(FluidState state,Operation<Stream<TagKey<Fluid>>> original){Stream<TagKey<Fluid>> tags=original.call(state);return state.getType().isSame(InkRegistry.BLACK_INK.get())?Stream.concat(tags,Stream.of(FluidTags.WATER)):tags;}
}
