/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Forge's {@code TerrainParticle.updateSprite}, injected into {@link TerrainParticle} on Fabric: the particle takes
 * the sprite of the given block.
 */
public interface IForgeTerrainParticle {

    default TerrainParticle updateSprite(BlockState state, BlockPos pos) {
        TerrainParticle self = (TerrainParticle) this;
        self.setSprite(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state));
        return self;
    }
}
