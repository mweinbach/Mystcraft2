/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions;

import com.mojang.math.Vector3f;

/**
 * The component setters Forge adds to {@link Vector3f}, injected into it on Fabric.
 */
public interface IForgeVector3f {

    private Vector3f self() {
        return (Vector3f) this;
    }

    default void mekanism$setX(float x) {
        self().set(x, self().y(), self().z());
    }

    default void mekanism$setY(float y) {
        self().set(self().x(), y, self().z());
    }

    default void mekanism$setZ(float z) {
        self().set(self().x(), self().y(), z);
    }
}
