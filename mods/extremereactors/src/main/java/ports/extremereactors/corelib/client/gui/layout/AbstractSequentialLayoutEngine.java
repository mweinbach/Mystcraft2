/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.layout;

import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.IControlContainer;
import ports.extremereactors.corelib.data.geometry.Rectangle;

import java.util.List;

/**
 * Places the children one after the other along an axis, {@code spacing} pixels apart.
 * <p>
 * Along the axis a child takes its desired size; children without one share the room that is left equally. Across
 * the axis a child takes its desired size, or the whole room, and is aligned in it.
 */
public abstract class AbstractSequentialLayoutEngine<E extends AbstractSequentialLayoutEngine<E>>
        extends AbstractLayoutEngine<E> {

    public E setControlsSpacing(final int spacing) {

        this._spacing = Math.max(0, spacing);
        return this.self();
    }

    public E setHorizontalAlignment(final HorizontalAlignment alignment) {

        this._horizontalAlignment = alignment;
        return this.self();
    }

    public E setVerticalAlignment(final VerticalAlignment alignment) {

        this._verticalAlignment = alignment;
        return this.self();
    }

    public int getControlsSpacing() {
        return this._spacing;
    }

    public HorizontalAlignment getHorizontalAlignment() {
        return this._horizontalAlignment;
    }

    public VerticalAlignment getVerticalAlignment() {
        return this._verticalAlignment;
    }

    //region ILayoutEngine

    @Override
    public void layout(final IControlContainer container) {

        final List<IControl> controls = container.getActiveControls();

        if (controls.isEmpty()) {
            return;
        }

        final boolean horizontal = this.isHorizontal();
        final DesiredDimension along = horizontal ? DesiredDimension.Width : DesiredDimension.Height;
        final DesiredDimension across = horizontal ? DesiredDimension.Height : DesiredDimension.Width;
        final Rectangle area = this.getLayoutArea(container);
        final int room = horizontal ? area.Width : area.Height;
        final int crossRoom = horizontal ? area.Height : area.Width;
        final int[] sizes = new int[controls.size()];
        int used = this._spacing * (controls.size() - 1);
        int flexible = 0;

        for (int i = 0; i < sizes.length; ++i) {

            sizes[i] = desired(controls.get(i), along, 0);

            if (sizes[i] > 0) {
                used += sizes[i];
            } else {
                ++flexible;
            }
        }

        final int share = flexible > 0 ? Math.max(0, (room - used) / flexible) : 0;
        final int total = used + share * flexible;
        int position = horizontal ?
                this._horizontalAlignment.align(area.getX1(), area.Width, total) :
                this._verticalAlignment.align(area.getY1(), area.Height, total);

        for (int i = 0; i < sizes.length; ++i) {

            final IControl control = controls.get(i);
            final int size = sizes[i] > 0 ? sizes[i] : share;
            final int crossSize = desired(control, across, crossRoom);

            if (horizontal) {
                control.setBounds(new Rectangle(position,
                        this._verticalAlignment.align(area.getY1(), area.Height, crossSize), size, crossSize));
            } else {
                control.setBounds(new Rectangle(this._horizontalAlignment.align(area.getX1(), area.Width, crossSize),
                        position, crossSize, size));
            }

            position += size + this._spacing;
        }
    }

    //endregion
    //region internals

    protected AbstractSequentialLayoutEngine(final HorizontalAlignment horizontalAlignment,
                                             final VerticalAlignment verticalAlignment) {

        this._horizontalAlignment = horizontalAlignment;
        this._verticalAlignment = verticalAlignment;
    }

    protected abstract boolean isHorizontal();

    private int _spacing;
    private HorizontalAlignment _horizontalAlignment;
    private VerticalAlignment _verticalAlignment;

    //endregion
}
