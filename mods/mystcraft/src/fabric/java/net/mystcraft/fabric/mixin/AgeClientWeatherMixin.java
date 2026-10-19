package net.mystcraft.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.mystcraft.minecraft.age.AgeWeatherRules;
import net.mystcraft.minecraft.client.age.AgeVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class AgeClientWeatherMixin {
    @Inject(method = "getTemperature", at = @At("RETURN"), cancellable = true)
    private void mystcraft$temperature(BlockPos pos, CallbackInfoReturnable<Float> callback) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return;
        callback.setReturnValue(AgeWeatherRules.temperature(AgeVisualClient.settings(client.level).environment().weather(), callback.getReturnValue()));
    }
    @Inject(method = "getPrecipitation", at = @At("RETURN"), cancellable = true)
    private void mystcraft$precipitation(CallbackInfoReturnable<Biome.Precipitation> callback) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) return;
        ClientLevel level = client.level;
        callback.setReturnValue(AgeWeatherRules.precipitation(AgeVisualClient.settings(level).environment().weather(), callback.getReturnValue(), level != null && level.isRaining()));
    }
}
