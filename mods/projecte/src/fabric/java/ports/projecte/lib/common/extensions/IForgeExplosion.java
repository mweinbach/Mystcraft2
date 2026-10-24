/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import org.jetbrains.annotations.Nullable;

/**
 * Forge's {@code Explosion.getExploder}, injected into {@link Explosion} on Fabric.
 */
public interface IForgeExplosion {

    @Nullable
    default Entity getExploder() {
        return ((Explosion) this).source;
    }
}
