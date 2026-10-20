/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib;

import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/**
 * A sink for human-readable diagnostic lines. Nested sections are indented under their label.
 */
public interface IDebugMessages {

    IDebugMessages add(Component message);

    IDebugMessages add(LogicalSide side, IDebuggable debuggable, String label);

    <T> IDebugMessages add(T object, BiConsumer<IDebugMessages, T> provider, String label);

    default IDebugMessages addUnlocalized(final String format, final Object... arguments) {
        return this.add(Component.literal(0 == arguments.length ? format : String.format(format, arguments)));
    }
}
