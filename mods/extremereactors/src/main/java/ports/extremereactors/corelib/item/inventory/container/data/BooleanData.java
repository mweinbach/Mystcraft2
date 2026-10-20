/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.BooleanSupplier;

public class BooleanData
        extends AbstractData<Boolean> {

    public BooleanData(final BooleanSupplier source, final BooleanConsumer consumer) {
        super(source::getAsBoolean, consumer::accept);
    }

    @Override
    protected void write(final FriendlyByteBuf buffer, final Boolean value) {
        buffer.writeBoolean(value);
    }

    @Override
    protected Boolean read(final FriendlyByteBuf buffer) {
        return buffer.readBoolean();
    }
}
