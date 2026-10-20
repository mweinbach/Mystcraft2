/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.databind;

import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.fluid.FluidStack;

import java.util.Objects;
import java.util.function.Function;

/**
 * A binding that reads its value with a function of a source object and only passes on changed values.
 * <p>
 * Item and fluid stacks are mutable, so they are compared by content and a copy is kept.
 */
public abstract class AbstractBinding<Source, Value>
        implements IBinding {

    //region IBinding

    @Override
    public void update() {

        final Value value = this._getter.apply(this._source);

        if (this._hasValue && same(this._lastValue, value)) {
            return;
        }

        this._hasValue = true;
        this._lastValue = snapshot(value);
        this.dispatch(value);
    }

    //endregion
    //region internals

    protected AbstractBinding(final Source source, final Function<Source, Value> getter) {

        this._source = Objects.requireNonNull(source);
        this._getter = Objects.requireNonNull(getter);
    }

    protected abstract void dispatch(Value value);

    static boolean same(final Object previous, final Object current) {

        if (previous instanceof ItemStack a && current instanceof ItemStack b) {
            return ItemStack.matches(a, b);
        }

        if (previous instanceof FluidStack a && current instanceof FluidStack b) {
            return a.isFluidStackIdentical(b);
        }

        return Objects.equals(previous, current);
    }

    @SuppressWarnings("unchecked")
    static <V> V snapshot(final V value) {

        if (value instanceof ItemStack stack) {
            return (V) stack.copy();
        }

        if (value instanceof FluidStack stack) {
            return (V) stack.copy();
        }

        return value;
    }

    private final Source _source;
    private final Function<Source, Value> _getter;
    private boolean _hasValue;
    private Value _lastValue;

    //endregion
}
