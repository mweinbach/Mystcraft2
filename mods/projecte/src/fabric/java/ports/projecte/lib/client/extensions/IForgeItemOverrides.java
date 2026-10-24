/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.renderer.block.model.ItemOverrides;

/**
 * Forge's {@code ItemOverrides.getOverrides}, injected into {@link ItemOverrides} on Fabric.
 */
public interface IForgeItemOverrides {

    default ImmutableList<ItemOverrides.BakedOverride> getOverrides() {
        return ImmutableList.copyOf(((ItemOverrides) this).overrides);
    }
}
