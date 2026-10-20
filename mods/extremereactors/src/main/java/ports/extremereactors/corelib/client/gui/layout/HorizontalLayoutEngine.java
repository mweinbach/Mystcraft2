/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.layout;

/**
 * Lines the children up from left to right. By default they start at the left and are centered vertically.
 */
public class HorizontalLayoutEngine
        extends AbstractSequentialLayoutEngine<HorizontalLayoutEngine> {

    public HorizontalLayoutEngine() {
        super(HorizontalAlignment.Left, VerticalAlignment.Center);
    }

    @Override
    protected boolean isHorizontal() {
        return true;
    }
}
