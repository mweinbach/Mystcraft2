/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions;

import ports.projecte.lib.client.model.geometry.BlockGeometryBakingContext;

/**
 * Forge's {@code BlockModel.customData} field, injected into {@link net.minecraft.client.renderer.block.model.BlockModel}
 * on Fabric as an accessor. The port's build rewrites Mekanism's field reads to this method.
 */
public interface IForgeBlockModel {

    default BlockGeometryBakingContext getCustomData() {
        throw new IllegalStateException("BlockModel mixin not applied");
    }
}
