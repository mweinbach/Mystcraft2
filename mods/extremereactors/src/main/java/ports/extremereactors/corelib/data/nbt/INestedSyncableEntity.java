/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.nbt;

import java.util.Optional;

/** A block entity whose client sync is delegated to another entity, e.g. its multiblock controller. */
public interface INestedSyncableEntity {

    Optional<ISyncableEntity> getNestedSyncableEntity();
}
