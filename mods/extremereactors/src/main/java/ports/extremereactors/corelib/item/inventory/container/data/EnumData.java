/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class EnumData<E extends Enum<E>>
        extends AbstractData<E> {

    public EnumData(final Class<E> enumClass, final Supplier<E> source, final Consumer<E> consumer) {

        super(source, consumer);
        this._enumClass = enumClass;
    }

    @Override
    protected void write(final FriendlyByteBuf buffer, final E value) {
        buffer.writeEnum(value);
    }

    @Override
    protected E read(final FriendlyByteBuf buffer) {
        return buffer.readEnum(this._enumClass);
    }

    private final Class<E> _enumClass;
}
