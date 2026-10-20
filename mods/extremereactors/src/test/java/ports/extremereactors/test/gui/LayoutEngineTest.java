/*
 * Extreme Reactors 1.19.2 port - tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.test.gui;

import org.junit.jupiter.api.Test;
import ports.extremereactors.corelib.client.gui.Padding;
import ports.extremereactors.corelib.client.gui.layout.AnchoredLayoutEngine;
import ports.extremereactors.corelib.client.gui.layout.FixedLayoutEngine;
import ports.extremereactors.corelib.client.gui.layout.HorizontalAlignment;
import ports.extremereactors.corelib.client.gui.layout.HorizontalLayoutEngine;
import ports.extremereactors.corelib.client.gui.layout.TabularLayoutEngine;
import ports.extremereactors.corelib.client.gui.layout.VerticalLayoutEngine;
import ports.extremereactors.corelib.data.geometry.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where the layout engines put children, for the arrangements the reactor, turbine and machine screens use.
 */
class LayoutEngineTest {

    @Test
    void fixedLayoutPlacesChildrenAtTheirHintsInsidePaddingAndMargins() {

        final FakeControl hinted = FakeControl.sized(18, 18).hint(FixedLayoutEngine.hint(10, 20));
        final FakeControl overridden = FakeControl.sized(18, 18).hint(FixedLayoutEngine.hint(0, 0, 40, 5));
        final FakeControl unhinted = FakeControl.sized(7, 9);
        final FakeControl container = FakeControl.container(100, 100,
                new FixedLayoutEngine().setHorizontalMargin(3).setVerticalMargin(4), hinted, overridden, unhinted);

        container.setPadding(Padding.get(1, 0, 2, 0));
        container.layout();

        assertEquals(new Rectangle(14, 26, 18, 18), hinted.getBounds());
        assertEquals(new Rectangle(4, 6, 40, 5), overridden.getBounds());
        assertEquals(new Rectangle(4, 6, 7, 9), unhinted.getBounds());
    }

    @Test
    void verticalLayoutStacksWithSpacingAndSharesTheRestAmongUnsizedChildren() {

        final FakeControl header = FakeControl.sized(0, 10);
        final FakeControl body = FakeControl.sized(0, 0);
        final FakeControl footer = FakeControl.sized(20, 0);
        final FakeControl container = FakeControl.container(60, 100,
                new VerticalLayoutEngine().setZeroMargins().setControlsSpacing(5), header, body, footer);

        container.layout();

        // 100 - 10 - 2 * 5 = 80 left, shared by the two children without a height.
        assertEquals(new Rectangle(0, 0, 60, 10), header.getBounds());
        assertEquals(new Rectangle(0, 15, 60, 40), body.getBounds());
        assertEquals(new Rectangle(20, 60, 20, 40), footer.getBounds());
    }

    @Test
    void horizontalLayoutAlignsTheRowAndCentersChildrenVertically() {

        final FakeControl a = FakeControl.sized(10, 10);
        final FakeControl b = FakeControl.sized(20, 30);
        final FakeControl container = FakeControl.container(100, 50,
                new HorizontalLayoutEngine().setZeroMargins().setControlsSpacing(4)
                        .setHorizontalAlignment(HorizontalAlignment.Right), a, b);

        container.layout();

        assertEquals(new Rectangle(66, 20, 10, 10), a.getBounds());
        assertEquals(new Rectangle(80, 10, 20, 30), b.getBounds());
    }

    @Test
    void tabularLayoutFillsCellsRowByRowAndDropsChildrenPastTheLastRow() {

        final FakeControl[] cells = new FakeControl[5];

        for (int i = 0; i < cells.length; ++i) {
            cells[i] = FakeControl.sized(10, 0);
        }

        cells[3].hint(TabularLayoutEngine.hintBuilder().setPadding(Padding.get(0, 0, 2, 0)).build());

        final FakeControl container = FakeControl.container(70, 50,
                TabularLayoutEngine.builder().columns(2, 30).rows(2, 20).build().setZeroMargins(), cells);

        container.layout();

        // Column 1 takes the 40 pixels left over from column 0, row 1 the 30 left over from row 0.
        assertEquals(new Rectangle(10, 0, 10, 20), cells[0].getBounds());
        assertEquals(new Rectangle(45, 0, 10, 20), cells[1].getBounds());
        assertEquals(new Rectangle(10, 20, 10, 30), cells[2].getBounds());
        assertEquals(new Rectangle(45, 22, 10, 28), cells[3].getBounds());
        assertEquals(Rectangle.ZERO, cells[4].getBounds());
    }

    @Test
    void anchoredLayoutPinsChildrenToTheirAnchors() {

        final FakeControl topLeft = FakeControl.sized(10, 10);
        final FakeControl center = FakeControl.sized(10, 20).hint(AnchoredLayoutEngine.Anchor.Center);
        final FakeControl bottomRight = FakeControl.sized(30, 5).hint(AnchoredLayoutEngine.Anchor.BottomRight);
        final FakeControl fill = FakeControl.sized(0, 0).hint(AnchoredLayoutEngine.Anchor.Top);
        final FakeControl container = FakeControl.container(100, 60,
                new AnchoredLayoutEngine().setZeroMargins(), topLeft, center, bottomRight, fill);

        container.layout();

        assertEquals(new Rectangle(0, 0, 10, 10), topLeft.getBounds());
        assertEquals(new Rectangle(45, 20, 10, 20), center.getBounds());
        assertEquals(new Rectangle(70, 55, 30, 5), bottomRight.getBounds());
        assertEquals(new Rectangle(0, 0, 100, 60), fill.getBounds());
    }
}
