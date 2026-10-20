/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class IntData
        extends AbstractData<Integer> {

    public IntData(final IntSupplier source, final IntConsumer consumer) {
        super(source::getAsInt, consumer::accept);
    }

    @Override
    protected void write(final FriendlyByteBuf buffer, final Integer value) {
        buffer.writeVarInt(value);
    }

    @Override
    protected Integer read(final FriendlyByteBuf buffer) {
        return buffer.readVarInt();
    }
}
