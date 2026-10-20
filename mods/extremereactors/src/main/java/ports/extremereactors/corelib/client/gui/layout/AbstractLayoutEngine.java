/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.layout;

import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.IControlContainer;
import ports.extremereactors.corelib.data.geometry.Rectangle;

/**
 * A layout engine that keeps a margin between the container padding and its children.
 *
 * @param <E> the concrete engine, so the setters can be chained
 */
public abstract class AbstractLayoutEngine<E extends AbstractLayoutEngine<E>>
        implements ILayoutEngine {

    public E setHorizontalMargin(final int margin) {

        this._horizontalMargin = Math.max(0, margin);
        return this.self();
    }

    public E setVerticalMargin(final int margin) {

        this._verticalMargin = Math.max(0, margin);
        return this.self();
    }

    public E setZeroMargins() {

        this._horizontalMargin = this._verticalMargin = 0;
        return this.self();
    }

    public int getHorizontalMargin() {
        return this._horizontalMargin;
    }

    public int getVerticalMargin() {
        return this._verticalMargin;
    }

    //region internals

    protected AbstractLayoutEngine() {
    }

    @SuppressWarnings("unchecked")
    protected E self() {
        return (E) this;
    }

    /** The room the children can use, in container coordinates. */
    protected Rectangle getLayoutArea(final IControlContainer container) {
        return container.getPaddingRect().inset(this._horizontalMargin, this._horizontalMargin,
                this._verticalMargin, this._verticalMargin);
    }

    /** The desired width or height of a control, or {@code fallback} if it has none. */
    protected static int desired(final IControl control, final DesiredDimension dimension, final int fallback) {

        final int value = control.getDesiredDimension(dimension);

        return value > 0 ? value : fallback;
    }

    private int _horizontalMargin;
    private int _verticalMargin;

    //endregion
}
