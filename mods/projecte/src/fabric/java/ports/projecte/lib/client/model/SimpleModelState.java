/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model;

import com.mojang.math.Transformation;
import net.minecraft.client.resources.model.ModelState;

public final class SimpleModelState implements ModelState {

    private final Transformation transformation;
    private final boolean uvLocked;

    public SimpleModelState(Transformation transformation, boolean uvLocked) {
        this.transformation = transformation;
        this.uvLocked = uvLocked;
    }

    public SimpleModelState(Transformation transformation) {
        this(transformation, false);
    }

    @Override
    public Transformation getRotation() {
        return transformation;
    }

    @Override
    public boolean isUvLocked() {
        return uvLocked;
    }
}
