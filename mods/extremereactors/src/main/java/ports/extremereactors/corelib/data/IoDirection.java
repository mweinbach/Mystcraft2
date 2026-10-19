/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data;

import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.corelib.IDebugMessages;
import ports.extremereactors.corelib.IDebuggable;
import ports.extremereactors.corelib.LogicalSide;

/** Whether a port moves things into or out of its machine. */
public enum IoDirection implements IDebuggable {

    Input,
    Output;

    public boolean isInput() {
        return this == Input;
    }

    public boolean isOutput() {
        return this == Output;
    }

    public IoDirection opposite() {
        return this == Input ? Output : Input;
    }

    public static IoDirection read(final CompoundTag data, final String key, final IoDirection defaultValue) {
        return data.contains(key) ? (data.getBoolean(key) ? Input : Output) : defaultValue;
    }

    public static CompoundTag write(final CompoundTag data, final String key, final IoDirection value) {

        data.putBoolean(key, value.isInput());
        return data;
    }

    @Override
    public void getDebugMessages(final LogicalSide side, final IDebugMessages messages) {
        messages.addUnlocalized("IO direction: %s", this.name());
    }
}
