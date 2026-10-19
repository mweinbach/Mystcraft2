package net.mystcraft.minecraft.client.age;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/** Vanilla Overworld sky and weather geometry, with a per-Age fog provider. */
public class AgeDimensionEffects extends DimensionSpecialEffects.OverworldEffects {
    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return AgeVisualClient.fog(super.getBrightnessDependentFogColor(color, brightness), brightness);
    }
}
