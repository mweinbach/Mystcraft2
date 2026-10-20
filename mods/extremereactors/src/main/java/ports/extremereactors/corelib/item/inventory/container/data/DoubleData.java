/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import it.unimi.dsi.fastutil.doubles.DoubleConsumer;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.DoubleSupplier;

public class DoubleData
        extends AbstractData<Double> {

    public DoubleData(final DoubleSupplier source, final DoubleConsumer consumer) {
        super(source::getAsDouble, consumer::accept);
    }

    @Override
    protected void write(final FriendlyByteBuf buffer, final Double value) {
        buffer.writeDouble(value);
    }

    @Override
    protected Double read(final FriendlyByteBuf buffer) {
        return buffer.readDouble();
    }
}
