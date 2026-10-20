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
 * Places each child where its {@link Hint} says, relative to the layout area. A child without a hint goes to the
 * top-left corner. A hint without a size uses the child's desired size.
 */
public class FixedLayoutEngine
        extends AbstractLayoutEngine<FixedLayoutEngine> {

    public record Hint(int x, int y, int width, int height) {
    }

    public static Hint hint(final int x, final int y, final int width, final int height) {
        return new Hint(x, y, width, height);
    }

    public static Hint hint(final int x, final int y) {
        return new Hint(x, y, 0, 0);
    }

    //region ILayoutEngine

    @Override
    public void layout(final IControlContainer container) {

        final Rectangle area = this.getLayoutArea(container);

        for (final IControl control : container.getActiveControls()) {

            final Hint hint = control.getLayoutEngineHint() instanceof Hint h ? h : DEFAULT_HINT;
            final int width = hint.width() > 0 ? hint.width() : desired(control, DesiredDimension.Width, 0);
            final int height = hint.height() > 0 ? hint.height() : desired(control, DesiredDimension.Height, 0);

            control.setBounds(new Rectangle(area.getX1() + hint.x(), area.getY1() + hint.y(), width, height));
        }
    }

    //endregion
    //region internals

    private static final Hint DEFAULT_HINT = new Hint(0, 0, 0, 0);

    //endregion
}
