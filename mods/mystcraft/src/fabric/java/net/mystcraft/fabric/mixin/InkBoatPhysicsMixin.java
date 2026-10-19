package net.mystcraft.fabric.mixin;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.mystcraft.integration.registry.InkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Mixin(Boat.class)
public abstract class InkBoatPhysicsMixin {
    @WrapOperation(method={"checkInWater","isUnderwater","getWaterLevelAbove","checkFallDamage"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean mystcraft$inkBuoyancy(FluidState state,TagKey<Fluid> tag,Operation<Boolean> original){return original.call(state,tag)||tag==FluidTags.WATER&&state.getType().isSame(InkRegistry.BLACK_INK.get());}
}
