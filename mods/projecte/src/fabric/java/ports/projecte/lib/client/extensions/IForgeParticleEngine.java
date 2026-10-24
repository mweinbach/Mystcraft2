/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import ports.projecte.lib.client.extensions.common.IClientBlockExtensions;

/**
 * Forge's {@code ParticleEngine.addBlockHitEffects}, injected into {@link ParticleEngine} on Fabric. The block's
 * client extensions may draw their own hit particles instead of vanilla's.
 */
public interface IForgeParticleEngine {

    default void addBlockHitEffects(BlockPos pos, BlockHitResult target) {
        ParticleEngine self = (ParticleEngine) this;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        if (!IClientBlockExtensions.of(state).addHitEffects(state, minecraft.level, target, self)) {
            self.crack(pos, target.getDirection());
        }
    }
}
