/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.computer;

import java.util.function.BiFunction;

/**
 * A method that a peripheral offers to the computers attached to it.
 *
 * @param <P> the peripheral the method runs on
 */
public final class ComputerMethod<P> {

    public ComputerMethod(final String name, final BiFunction<P, Object[], Object> handler) {
        this(name, handler, 0, false);
    }

    public ComputerMethod(final String name, final BiFunction<P, Object[], Object> handler, final int argumentsCount) {
        this(name, handler, argumentsCount, false);
    }

    /**
     * @param argumentsCount the minimum number of arguments the method needs
     * @param mainThreadOnly true if the method changes the world and must run on the server thread
     */
    public ComputerMethod(final String name, final BiFunction<P, Object[], Object> handler, final int argumentsCount,
                          final boolean mainThreadOnly) {

        this._name = name;
        this._handler = handler;
        this._argumentsCount = argumentsCount;
        this._mainThreadOnly = mainThreadOnly;
    }

    public String getName() {
        return this._name;
    }

    public int getArgumentsCount() {
        return this._argumentsCount;
    }

    public boolean isMainThreadOnly() {
        return this._mainThreadOnly;
    }

    /**
     * @return the method result, already converted by {@link LuaHelper#toLua(Object)}
     * @throws IllegalArgumentException if fewer arguments than needed are given or they are not valid
     * @throws IllegalStateException if the method cannot run now
     */
    public Object invoke(final P peripheral, final Object[] arguments) {

        if (arguments.length < this._argumentsCount) {
            throw new IllegalArgumentException(String.format("%s expects %d argument(s), got %d", this._name,
                    this._argumentsCount, arguments.length));
        }

        return LuaHelper.toLua(this._handler.apply(peripheral, arguments));
    }

    @Override
    public String toString() {
        return this._name;
    }

    //region internals

    private final String _name;
    private final BiFunction<P, Object[], Object> _handler;
    private final int _argumentsCount;
    private final boolean _mainThreadOnly;

    //endregion
}
