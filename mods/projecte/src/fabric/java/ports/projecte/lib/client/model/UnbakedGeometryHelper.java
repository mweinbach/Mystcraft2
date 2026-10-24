/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model;

import com.mojang.math.Transformation;
import net.minecraft.client.resources.model.ModelState;

public final class UnbakedGeometryHelper {

    private UnbakedGeometryHelper() {
    }

    /**
     * Applies a model's root transform under the block state rotation, the order Forge uses.
     */
    public static ModelState composeRootTransform(ModelState modelState, Transformation rootTransform) {
        if (rootTransform.projecte$isIdentity()) {
            return modelState;
        }
        return new SimpleModelState(modelState.getRotation().compose(rootTransform), modelState.isUvLocked());
    }
}
