/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client;

import net.minecraft.client.renderer.RenderType;

public record RenderTypeGroup(RenderType block, RenderType entity, RenderType entityFabulous) {

    public static RenderTypeGroup EMPTY = new RenderTypeGroup(null, null, null);

    public RenderTypeGroup {
        if ((block == null) != (entity == null) || (block == null) != (entityFabulous == null)) {
            throw new IllegalArgumentException("The render types in a group must either be all null, or all non-null.");
        }
    }

    public RenderTypeGroup(RenderType block, RenderType entity) {
        this(block, entity, entity);
    }

    public boolean isEmpty() {
        return block == null;
    }
}
