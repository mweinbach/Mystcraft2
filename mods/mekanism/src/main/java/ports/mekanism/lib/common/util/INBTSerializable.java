/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.util;

import net.minecraft.nbt.Tag;

public interface INBTSerializable<T extends Tag> {

    T serializeNBT();

    void deserializeNBT(T nbt);
}
