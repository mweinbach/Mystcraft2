/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.capabilities;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.util.LazyOptional;

public interface ICapabilityProvider {

    @NotNull
    <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side);

    @NotNull
    default <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap) {
        return getCapability(cap, null);
    }
}
