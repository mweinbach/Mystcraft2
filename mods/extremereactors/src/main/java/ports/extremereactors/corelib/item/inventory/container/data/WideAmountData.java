/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import net.minecraft.network.FriendlyByteBuf;
import ports.extremereactors.corelib.data.WideAmount;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class WideAmountData
        extends AbstractData<WideAmount> {

    public WideAmountData(final Supplier<WideAmount> source, final Consumer<WideAmount> consumer) {
        super(source, consumer);
    }

    @Override
    protected void write(final FriendlyByteBuf buffer, final WideAmount value) {
        value.serializeTo(buffer);
    }

    @Override
    protected WideAmount read(final FriendlyByteBuf buffer) {
        return WideAmount.from(buffer);
    }

    @Override
    protected WideAmount copy(final WideAmount value) {
        return value.copy();
    }
}
