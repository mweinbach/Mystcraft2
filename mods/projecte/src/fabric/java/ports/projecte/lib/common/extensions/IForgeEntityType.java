/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import java.util.stream.Stream;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Forge's {@code EntityType.getTags}, injected into {@link EntityType} on Fabric.
 */
public interface IForgeEntityType {

    @SuppressWarnings("deprecation")
    default Stream<TagKey<EntityType<?>>> getTags() {
        return ((EntityType<?>) this).builtInRegistryHolder().tags();
    }
}
