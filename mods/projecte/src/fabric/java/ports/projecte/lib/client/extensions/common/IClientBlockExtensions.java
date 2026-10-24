/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * Client behavior a block supplies through {@code initializeClient}. Mixins on the particle engine consult it before
 * vanilla's hit and destroy particles.
 */
public interface IClientBlockExtensions {

    IClientBlockExtensions DEFAULT = new IClientBlockExtensions() {
    };

    static IClientBlockExtensions of(BlockState state) {
        return of(state.getBlock());
    }

    static IClientBlockExtensions of(Block block) {
        return Cache.BY_BLOCK.computeIfAbsent(block, b -> {
            IClientBlockExtensions[] holder = {DEFAULT};
            b.initializeClient(extensions -> holder[0] = extensions);
            return holder[0];
        });
    }

    /**
     * @return {@code true} to skip vanilla's hit particles
     */
    default boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
        return false;
    }

    /**
     * @return {@code true} to skip vanilla's destroy particles
     */
    default boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        return false;
    }

    final class Cache {

        private static final Map<Block, IClientBlockExtensions> BY_BLOCK = new ConcurrentHashMap<>();

        private Cache() {
        }
    }
}
