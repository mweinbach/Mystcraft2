package net.mystcraft.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.mystcraft.minecraft.client.age.AgeDimensionEffects;
import net.mystcraft.minecraft.client.age.AgeSkyRenderer;

/** Forge's native sky extension suppresses its vanilla bodies only for a realized custom plan. */
public final class ForgeAgeDimensionEffects extends AgeDimensionEffects {
    @Override public boolean renderSky(ClientLevel level, int ticks, float partial, PoseStack pose, Camera camera,
            Matrix4f projection, boolean foggy, Runnable setupFog) {
        return AgeSkyRenderer.render(level, pose, projection, partial, camera, foggy, setupFog);
    }
}
