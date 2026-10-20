/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container;

import com.google.common.collect.ImmutableList;
import ports.extremereactors.corelib.item.inventory.container.slot.SlotTemplate;
import ports.extremereactors.corelib.item.inventory.container.slot.type.SlotType;

import java.util.ArrayList;
import java.util.List;

/**
 * The slot layout of a container: which slot of which named inventory goes where. Containers resolve the inventory
 * names when they create their slots; see {@link ModContainer#addInventory}.
 * <p>
 * Subclasses can describe their layout in {@link #onAddSlots()}, which runs once, the first time the layout is used.
 */
public class ContainerFactory {

    /** A layout without slots. */
    public static final ContainerFactory EMPTY = new ContainerFactory() {

        @Override
        public ContainerFactory addSlot(final int index, final String inventoryName, final SlotTemplate template,
                                        final int x, final int y) {
            throw new UnsupportedOperationException("The empty container factory cannot have slots");
        }
    };

    public record SlotEntry(int index, String inventoryName, SlotTemplate template, int x, int y) {
    }

    public ContainerFactory() {
        this._entries = new ArrayList<>();
    }

    public ContainerFactory addSlot(final int index, final String inventoryName, final SlotTemplate template,
                                    final int x, final int y) {

        this._entries.add(new SlotEntry(index, inventoryName, template, x, y));
        return this;
    }

    /**
     * Add the 27 main player inventory slots, in three rows starting at (x, y), and the 9 hotbar slots in a row
     * 58 pixels below. Slots are 18 pixels apart, as in vanilla containers.
     */
    public ContainerFactory addStandardPlayerInventorySlots(final int x, final int y) {

        final SlotTemplate inventory = new SlotTemplate(SlotType.PlayerInventory);
        final SlotTemplate hotbar = new SlotTemplate(SlotType.PlayerHotBar);

        for (int row = 0; row < 3; ++row) {
            for (int column = 0; column < 9; ++column) {
                this.addSlot(9 + row * 9 + column, ModContainer.INVENTORYNAME_PLAYER_INVENTORY, inventory,
                        x + column * 18, y + row * 18);
            }
        }

        for (int column = 0; column < 9; ++column) {
            this.addSlot(column, ModContainer.INVENTORYNAME_PLAYER_INVENTORY, hotbar, x + column * 18, y + 58);
        }

        return this;
    }

    public List<SlotEntry> getSlots() {

        if (!this._initialized) {

            this._initialized = true;
            this.onAddSlots();
        }

        return ImmutableList.copyOf(this._entries);
    }

    //region internals

    protected void onAddSlots() {
    }

    private final List<SlotEntry> _entries;
    private boolean _initialized;

    //endregion
}
