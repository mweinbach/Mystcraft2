/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.layout;

/**
 * Stacks the children from top to bottom. By default they are centered horizontally and start at the top.
 */
public class VerticalLayoutEngine
        extends AbstractSequentialLayoutEngine<VerticalLayoutEngine> {

    public VerticalLayoutEngine() {
        super(HorizontalAlignment.Center, VerticalAlignment.Top);
    }

    @Override
    protected boolean isHorizontal() {
        return false;
    }
}
