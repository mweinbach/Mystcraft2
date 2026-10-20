/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.data.geometry.Point;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;
import ports.extremereactors.corelib.item.inventory.container.slot.type.SlotType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * A grid of container slots, 18 pixels apart, over an optional background.
 * <p>
 * The group takes the slots of a named inventory of the container (optionally of one slot type only), in inventory
 * order, row by row. Each slot item is drawn {@code slotOffset} pixels inside its grid cell.
 */
public class SlotsGroup
        extends AbstractControl {

    public static final int SLOT_SIZE = 18;

    public SlotsGroup(final ModContainerScreen<? extends ModContainer> gui, final String name,
                      final String inventoryName, @Nullable final SlotType slotType, final int columns,
                      final int rows, final ISprite background, final int slotOffset) {

        super(gui, name);
        this._inventoryName = Objects.requireNonNull(inventoryName);
        this._slotType = slotType;
        this._columns = Math.max(1, columns);
        this._rows = Math.max(1, rows);
        this._slotOffset = slotOffset;

        if (!background.isEmpty()) {
            this.setBackground(background);
        }
    }

    /**
     * Move the slots of this group under it.
     *
     * @return the container indices of the slots moved
     */
    public List<Integer> positionSlots(final ModContainer container) {

        final List<Integer> indices = container.getSlotIndices(this._inventoryName, this._slotType);
        final Point origin = this.controlToScreen(0, 0);
        final int x = origin.X - this.getGui().getGuiLeft() + this._slotOffset;
        final int y = origin.Y - this.getGui().getGuiTop() + this._slotOffset;
        final int count = Math.min(indices.size(), this._columns * this._rows);

        for (int i = 0; i < count; ++i) {
            container.repositionSlot(indices.get(i), x + (i % this._columns) * SLOT_SIZE,
                    y + (i / this._columns) * SLOT_SIZE);
        }

        return indices.subList(0, count);
    }

    //region AbstractControl

    @Override
    protected int getDefaultDimension(final DesiredDimension dimension) {
        return (DesiredDimension.Width == dimension ? this._columns : this._rows) * SLOT_SIZE;
    }

    //endregion
    //region internals

    private final String _inventoryName;
    @Nullable
    private final SlotType _slotType;
    private final int _columns;
    private final int _rows;
    private final int _slotOffset;

    //endregion
}
