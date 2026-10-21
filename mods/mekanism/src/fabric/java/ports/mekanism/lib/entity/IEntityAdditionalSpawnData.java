/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.entity;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Extra data an entity writes into its spawn packet, sent by the port's own spawn packet on Fabric.
 */
public interface IEntityAdditionalSpawnData {

    void writeSpawnData(FriendlyByteBuf buffer);

    void readSpawnData(FriendlyByteBuf additionalData);
}
