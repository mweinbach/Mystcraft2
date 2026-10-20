/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.nbt;

/** Something whose state can absorb another instance's, e.g. when two multiblocks merge. */
public interface IMergeableEntity {

    void syncDataFrom(IMergeableEntity other);
}
