/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.slot.type;

/**
 * The role of a slot in a container. It decides what players can do with the slot and where shift-clicked stacks go.
 */
public enum SlotType {

    /** A slot of the main player inventory. */
    PlayerInventory,
    /** A slot of the player hotbar. */
    PlayerHotBar,
    /** A machine slot players can put items in and take items from. */
    Input,
    /** A machine slot players can only take items from. */
    Output,
    /** A display-only slot: players can neither put nor take items. */
    Static;

    public boolean isPlayerSlot() {
        return this == PlayerInventory || this == PlayerHotBar;
    }

    public boolean acceptsPlayerItems() {
        return this != Output && this != Static;
    }
}
