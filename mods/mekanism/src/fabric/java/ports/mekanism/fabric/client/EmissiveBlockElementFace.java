/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.client;

/**
 * Forge stores a face's {@code emissivity} on {@code BlockElementFace}. The mixin that reads the JSON and the mixin
 * that bakes the quad share it through this interface.
 */
public interface EmissiveBlockElementFace {

    int mekanism$emissivity();

    void mekanism$setEmissivity(int emissivity);
}
