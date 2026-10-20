/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.slot;

import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.item.inventory.container.slot.type.SlotType;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * How a slot behaves: its type and, for slots players may fill, which stacks it accepts (by slot index).
 */
public final class SlotTemplate {

    public SlotTemplate(final SlotType type) {
        this(type, (index, stack) -> true);
    }

    public SlotTemplate(final SlotType type, final BiPredicate<Integer, ItemStack> validator) {

        this._type = Objects.requireNonNull(type);
        this._validator = Objects.requireNonNull(validator);
    }

    public SlotType getType() {
        return this._type;
    }

    public boolean canPlace(final int index, final ItemStack stack) {
        return this._type.acceptsPlayerItems() && this._validator.test(index, stack);
    }

    //region internals

    private final SlotType _type;
    private final BiPredicate<Integer, ItemStack> _validator;

    //endregion
}
