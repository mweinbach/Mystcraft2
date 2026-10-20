/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block;

import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.corelib.LogicalSide;

@FunctionalInterface
public interface ITileCommandHandler {

    ITileCommandHandler NONE = (side, name, parameters) -> {};

    /**
     * @param sourceSide the side that sent the command
     */
    void handleCommand(LogicalSide sourceSide, String name, CompoundTag parameters);
}
