/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions;

import com.mojang.math.Transformation;
import net.minecraft.core.Direction;

/**
 * Methods Forge adds to {@link Transformation}, injected into it on Fabric.
 */
public interface IForgeTransformation {

    private Transformation self() {
        return (Transformation) this;
    }

    default boolean mekanism$isIdentity() {
        return self().equals(Transformation.identity());
    }

    default Direction mekanism$rotateTransform(Direction facing) {
        return Direction.rotate(self().getMatrix(), facing);
    }
}
