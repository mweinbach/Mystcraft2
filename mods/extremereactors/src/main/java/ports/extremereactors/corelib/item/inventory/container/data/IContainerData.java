/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import net.minecraft.network.FriendlyByteBuf;

/**
 * A value a container keeps in sync from the server to the client.
 */
public interface IContainerData {

    /**
     * Server side: read the current value from its source.
     *
     * @return true if it changed since it was last sent (always true the first time)
     */
    boolean updateFromSource();

    /** Server side: write the value read by the last {@link #updateFromSource()}. */
    void encode(FriendlyByteBuf buffer);

    /** Client side: read a value written by {@link #encode(FriendlyByteBuf)} and pass it to the consumer. */
    void decode(FriendlyByteBuf buffer);
}
