/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event;

import net.minecraft.core.RegistryAccess;
import ports.mekanism.lib.eventbus.api.Event;

public class TagsUpdatedEvent extends Event {

    public enum UpdateCause {
        SERVER_DATA_LOAD,
        CLIENT_PACKET_RECEIVED
    }

    private final RegistryAccess registryAccess;
    private final UpdateCause updateCause;
    private final boolean integratedServer;

    public TagsUpdatedEvent(RegistryAccess registryAccess, boolean fromClientPacket, boolean isIntegratedServerConnection) {
        this.registryAccess = registryAccess;
        this.updateCause = fromClientPacket ? UpdateCause.CLIENT_PACKET_RECEIVED : UpdateCause.SERVER_DATA_LOAD;
        this.integratedServer = isIntegratedServerConnection;
    }

    public RegistryAccess getRegistryAccess() {
        return registryAccess;
    }

    public UpdateCause getUpdateCause() {
        return updateCause;
    }

    public boolean shouldUpdateStaticData() {
        return updateCause == UpdateCause.SERVER_DATA_LOAD || !integratedServer;
    }
}
