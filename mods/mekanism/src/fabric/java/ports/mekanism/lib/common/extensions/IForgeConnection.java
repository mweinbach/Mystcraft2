/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

/**
 * Forge's {@code Connection.getDirection}: the direction of the packets this side receives.
 */
public interface IForgeConnection {

    default PacketFlow getDirection() {
        return ((Connection) this).getReceiving();
    }
}
