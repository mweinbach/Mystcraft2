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
 * Pins each child, at its desired size, to the point of the layout area its {@link Anchor} hint names.
 * Children without a hint are pinned to the top-left corner.
 */
public class AnchoredLayoutEngine
        extends AbstractLayoutEngine<AnchoredLayoutEngine> {

    public enum Anchor {

        TopLeft(HorizontalAlignment.Left, VerticalAlignment.Top),
        Top(HorizontalAlignment.Center, VerticalAlignment.Top),
        TopRight(HorizontalAlignment.Right, VerticalAlignment.Top),
        Left(HorizontalAlignment.Left, VerticalAlignment.Center),
        Center(HorizontalAlignment.Center, VerticalAlignment.Center),
        Right(HorizontalAlignment.Right, VerticalAlignment.Center),
        BottomLeft(HorizontalAlignment.Left, VerticalAlignment.Bottom),
        Bottom(HorizontalAlignment.Center, VerticalAlignment.Bottom),
        BottomRight(HorizontalAlignment.Right, VerticalAlignment.Bottom);

        Anchor(final HorizontalAlignment horizontal, final VerticalAlignment vertical) {

            this._horizontal = horizontal;
            this._vertical = vertical;
        }

        private final HorizontalAlignment _horizontal;
        private final VerticalAlignment _vertical;
    }

    //region ILayoutEngine

    @Override
    public void layout(final IControlContainer container) {

        final Rectangle area = this.getLayoutArea(container);

        for (final IControl control : container.getActiveControls()) {

            final Anchor anchor = control.getLayoutEngineHint() instanceof Anchor a ? a : Anchor.TopLeft;
            final int width = desired(control, DesiredDimension.Width, area.Width);
            final int height = desired(control, DesiredDimension.Height, area.Height);

            control.setBounds(new Rectangle(anchor._horizontal.align(area.getX1(), area.Width, width),
                    anchor._vertical.align(area.getY1(), area.Height, height), width, height));
        }
    }

    //endregion
}
