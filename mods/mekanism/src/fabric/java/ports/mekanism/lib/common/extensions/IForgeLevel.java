/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import java.util.Collection;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * State Forge adds to {@link net.minecraft.world.level.Level}, injected into it on Fabric as methods a mixin
 * implements. Forge's {@code restoringBlockSnapshots} field becomes accessors the port's build rewrites Mekanism's field
 * accesses to; while it is set, blocks do not drop items.
 */
public interface IForgeLevel {

    default boolean isRestoringBlockSnapshots() {
        throw new IllegalStateException("Level mixin not applied");
    }

    default void setRestoringBlockSnapshots(boolean restoring) {
        throw new IllegalStateException("Level mixin not applied");
    }

    /**
     * Queues block entities loaded with their chunk; their {@code mekanism$onLoad} runs before the level next ticks block
     * entities.
     */
    default void mekanism$addFreshBlockEntities(Collection<BlockEntity> blockEntities) {
        throw new IllegalStateException("Level mixin not applied");
    }
}
