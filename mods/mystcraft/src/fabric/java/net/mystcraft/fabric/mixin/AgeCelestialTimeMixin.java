package net.mystcraft.fabric.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.dimension.DimensionType;
import net.mystcraft.minecraft.client.age.AgeVisualClient;
import net.mystcraft.minecraft.age.visual.CelestialMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native interface-default callers route through this declared, mapped dimension method. */
@Mixin(DimensionType.class)
public abstract class AgeCelestialTimeMixin {
    @Inject(method = "timeOfDay", at = @At("HEAD"), cancellable = true)
    private void mystcraft$celestialTime(long time, CallbackInfoReturnable<Float> callback) {
        Minecraft client = Minecraft.getInstance();
        // Integrated-server calls must retain the server Age level's own authoritative clock.
        if (!client.isSameThread() || client.level == null || client.level.dimensionType() != (Object)this) return;
        var settings = AgeVisualClient.settings(client.level).celestials();
        if (!settings.legacyVanilla()) callback.setReturnValue(CelestialMath.daylightAngle(settings, time, client.getFrameTime()));
    }
}
