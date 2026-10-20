/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.nbt;

import net.minecraft.resources.ResourceLocation;

/** A syncable entity, owned by a container, that only sends updates when it has changed. */
public interface IConditionallySyncableEntity extends ISyncableEntity {

    ResourceLocation getSyncableEntityId();

    /** True if an update must be sent now; implementations usually clear their dirty flag here. */
    boolean shouldSyncEntity();
}
