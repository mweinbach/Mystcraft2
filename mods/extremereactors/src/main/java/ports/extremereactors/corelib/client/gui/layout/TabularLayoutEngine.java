/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.layout;

import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.IControlContainer;
import ports.extremereactors.corelib.client.gui.Padding;
import ports.extremereactors.corelib.data.geometry.Rectangle;

import java.util.Arrays;
import java.util.List;

/**
 * Puts the children in the cells of a grid, row by row, one child per cell.
 * <p>
 * Columns and rows have a fixed size, or share the room left by the sized ones. In its cell a child takes its desired
 * size, or the cell size, and is centered horizontally below the top of the cell. A {@link Hint} adds padding inside
 * the cell.
 */
public class TabularLayoutEngine
        extends AbstractLayoutEngine<TabularLayoutEngine> {

    public record Hint(Padding padding) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static HintBuilder hintBuilder() {
        return new HintBuilder();
    }

    public static final class Builder {

        /** {@code count} columns; the first {@code widths.length} have the given widths. */
        public Builder columns(final int count, final int... widths) {

            this._columns = sizes(count, widths);
            return this;
        }

        /** {@code count} rows; the first {@code heights.length} have the given heights. */
        public Builder rows(final int count, final int... heights) {

            this._rows = sizes(count, heights);
            return this;
        }

        public TabularLayoutEngine build() {
            return new TabularLayoutEngine(this._columns, this._rows);
        }

        private static int[] sizes(final int count, final int[] values) {

            final int[] sizes = new int[Math.max(1, count)];

            System.arraycopy(values, 0, sizes, 0, Math.min(values.length, sizes.length));
            return sizes;
        }

        private Builder() {

            this._columns = new int[1];
            this._rows = new int[1];
        }

        private int[] _columns;
        private int[] _rows;
    }

    public static final class HintBuilder {

        public HintBuilder setPadding(final Padding padding) {

            this._padding = padding;
            return this;
        }

        public Hint build() {
            return new Hint(this._padding);
        }

        private HintBuilder() {
            this._padding = Padding.ZERO;
        }

        private Padding _padding;
    }

    //region ILayoutEngine

    @Override
    public void layout(final IControlContainer container) {

        final Rectangle area = this.getLayoutArea(container);
        final int[] widths = resolve(this._columns, area.Width);
        final int[] heights = resolve(this._rows, area.Height);
        final List<IControl> controls = container.getActiveControls();

        for (int index = 0; index < controls.size(); ++index) {

            final IControl control = controls.get(index);
            final int column = index % widths.length;
            final int row = index / widths.length;

            if (row >= heights.length) {

                control.setBounds(Rectangle.ZERO);
                continue;
            }

            final Padding padding = control.getLayoutEngineHint() instanceof Hint hint ? hint.padding() : Padding.ZERO;
            final int cellX = area.getX1() + Arrays.stream(widths, 0, column).sum() + padding.left();
            final int cellY = area.getY1() + Arrays.stream(heights, 0, row).sum() + padding.top();
            final int cellWidth = widths[column] - padding.horizontal();
            final int cellHeight = heights[row] - padding.vertical();
            final int width = desired(control, DesiredDimension.Width, cellWidth);
            final int height = desired(control, DesiredDimension.Height, cellHeight);

            control.setBounds(new Rectangle(HorizontalAlignment.Center.align(cellX, cellWidth, width), cellY,
                    width, height));
        }
    }

    //endregion
    //region internals

    private TabularLayoutEngine(final int[] columns, final int[] rows) {

        this._columns = columns;
        this._rows = rows;
    }

    private static int[] resolve(final int[] sizes, final int room) {

        final int[] resolved = sizes.clone();
        final int used = Arrays.stream(sizes).filter(size -> size > 0).sum();
        final int flexible = (int) Arrays.stream(sizes).filter(size -> size <= 0).count();
        final int share = flexible > 0 ? Math.max(0, (room - used) / flexible) : 0;

        for (int i = 0; i < resolved.length; ++i) {

            if (resolved[i] <= 0) {
                resolved[i] = share;
            }
        }

        return resolved;
    }

    private final int[] _columns;
    private final int[] _rows;

    //endregion
}
