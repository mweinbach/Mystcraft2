/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.capability;

import net.minecraft.core.Direction;

import javax.annotation.Nullable;

public interface ICapabilityProvider {

    /**
     * @param side the side being queried, or null for an internal / side-less query
     * @return the handler, or {@link LazyOptional#empty()} if this provider does not expose it on that side
     */
    <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side);
}
