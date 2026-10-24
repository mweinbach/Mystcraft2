/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions;

import net.minecraft.client.resources.model.ModelBakery;

/**
 * Forge's {@code ModelManager.getModelBakery}, injected into {@link net.minecraft.client.resources.model.ModelManager}
 * on Fabric. A mixin keeps the bakery of the last reload and implements this.
 */
public interface IForgeModelManager {

    default ModelBakery getModelBakery() {
        throw new IllegalStateException("ModelManager mixin not applied");
    }
}
