/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.nbt;

import net.minecraft.nbt.CompoundTag;

public interface ICompoundSerializable {

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag data);
}
