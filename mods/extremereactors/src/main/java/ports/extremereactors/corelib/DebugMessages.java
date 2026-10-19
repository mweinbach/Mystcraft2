/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A flat list of debug lines; nested sections are prefixed with two spaces per level.
 */
public class DebugMessages
        implements IDebugMessages {

    public DebugMessages() {
        this("");
    }

    private DebugMessages(final String indent) {

        this._indent = indent;
        this._lines = new ArrayList<>();
    }

    public List<Component> getLines() {
        return Collections.unmodifiableList(this._lines);
    }

    @Override
    public IDebugMessages add(final Component message) {

        this._lines.add(this._indent.isEmpty() ? message : Component.literal(this._indent).append(message));
        return this;
    }

    @Override
    public IDebugMessages add(final LogicalSide side, final IDebuggable debuggable, final String label) {
        return this.add(debuggable, (messages, d) -> d.getDebugMessages(side, messages), label);
    }

    @Override
    public <T> IDebugMessages add(final T object, final BiConsumer<IDebugMessages, T> provider, final String label) {

        this.add(Component.literal(label));

        final DebugMessages nested = new DebugMessages(this._indent + "  ");

        provider.accept(nested, object);
        this._lines.addAll(nested._lines);
        return this;
    }

    private final String _indent;
    private final List<Component> _lines;
}
