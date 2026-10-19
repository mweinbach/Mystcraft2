package net.mystcraft.minecraft.client.age;

import net.minecraft.core.BlockPos;

/** Loader mixin exposes the vanilla position-dependent biome temperature without mutating biomes. */
public interface AgeBiomeTemperature {
    float mystcraft$temperature(BlockPos pos);
}
