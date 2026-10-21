/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.capabilities;

import net.minecraft.nbt.Tag;
import ports.mekanism.lib.common.util.INBTSerializable;

public interface ICapabilitySerializable<T extends Tag> extends ICapabilityProvider, INBTSerializable<T> {
}
