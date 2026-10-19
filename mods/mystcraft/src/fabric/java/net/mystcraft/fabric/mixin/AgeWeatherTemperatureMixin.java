package net.mystcraft.fabric.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.mystcraft.minecraft.age.AgeWeatherContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class AgeWeatherTemperatureMixin {
    @Inject(method = "getTemperature", at = @At("RETURN"), cancellable = true)
    private void mystcraft$temperature(BlockPos pos, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(AgeWeatherContext.temperature(callback.getReturnValue()));
    }
}
