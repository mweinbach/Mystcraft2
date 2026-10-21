/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import java.util.Optional;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The state-aware pickup sound Forge adds to {@link BucketPickup}, injected into it on Fabric.
 */
public interface IForgeBucketPickup {

    default Optional<SoundEvent> getPickupSound(BlockState state) {
        return ((BucketPickup) this).getPickupSound();
    }
}
