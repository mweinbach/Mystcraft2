package net.mystcraft.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.mystcraft.minecraft.client.age.AgeSkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Conditional hook preserves native legacy skies across server changes; no permanent key registration. */
@Mixin(value = LevelRenderer.class, priority = 1200)
public abstract class AgeCelestialSkyMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void mystcraft$celestialSky(PoseStack pose, Matrix4f projection, float partial, Camera camera,
            boolean foggy, Runnable setupFog, CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (level != null && AgeSkyRenderer.render(level, pose, projection, partial, camera, foggy, setupFog)) callback.cancel();
    }
}
