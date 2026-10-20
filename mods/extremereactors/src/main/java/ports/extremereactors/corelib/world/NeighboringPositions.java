/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * The six positions adjacent to a center position, in {@link Direction} order. The instance is reusable: call
 * {@link #setTo(BlockPos)} to move it to a new center.
 */
public class NeighboringPositions implements Iterable<BlockPos> {

    private static final Direction[] DIRECTIONS = Direction.values();
    public static final int SIZE = DIRECTIONS.length;

    public NeighboringPositions() {

        this._positions = new BlockPos[SIZE];
        this._hashes = new long[SIZE];
        this.setTo(BlockPos.ZERO);
    }

    public NeighboringPositions(final BlockPos center) {

        this._positions = new BlockPos[SIZE];
        this._hashes = new long[SIZE];
        this.setTo(center);
    }

    public NeighboringPositions setTo(final BlockPos center) {

        for (int i = 0; i < SIZE; ++i) {

            this._positions[i] = center.relative(DIRECTIONS[i]);
            this._hashes[i] = this._positions[i].asLong();
        }

        return this;
    }

    public int size() {
        return SIZE;
    }

    public BlockPos get(final int index) {
        return this._positions[index];
    }

    public BlockPos get(final Direction direction) {
        return this._positions[direction.get3DDataValue()];
    }

    public long getHash(final int index) {
        return this._hashes[index];
    }

    public BlockPos[] toArray() {
        return Arrays.copyOf(this._positions, SIZE);
    }

    @Nonnull
    @Override
    public Iterator<BlockPos> iterator() {
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                return this._next < SIZE;
            }

            @Override
            public BlockPos next() {

                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                }

                return NeighboringPositions.this._positions[this._next++];
            }

            private int _next = 0;
        };
    }

    //region internals

    private final BlockPos[] _positions;
    private final long[] _hashes;

    //endregion
}
