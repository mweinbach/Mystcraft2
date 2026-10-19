/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block;

import net.minecraft.core.Direction;
import ports.extremereactors.corelib.block.property.BlockFacingsProperty;

import java.util.Optional;

/**
 * An immutable set of block faces, stored as a 6-bit mask indexed by {@link Direction#get3DDataValue()}.
 */
public final class BlockFacings {

    public static final BlockFacings NONE;
    public static final BlockFacings ALL;
    public static final BlockFacings DOWN;
    public static final BlockFacings UP;
    public static final BlockFacings NORTH;
    public static final BlockFacings SOUTH;
    public static final BlockFacings WEST;
    public static final BlockFacings EAST;
    public static final BlockFacings VERTICAL;
    public static final BlockFacings HORIZONTAL;
    public static final BlockFacings AXIS_X;
    public static final BlockFacings AXIS_Y;
    public static final BlockFacings AXIS_Z;

    public static BlockFacings fromMask(final int mask) {
        return CACHE[mask & 0x3f];
    }

    public static BlockFacings from(final boolean down, final boolean up, final boolean north, final boolean south,
                                    final boolean west, final boolean east) {
        return fromMask((down ? 1 : 0) | (up ? 2 : 0) | (north ? 4 : 0) | (south ? 8 : 0) | (west ? 16 : 0) | (east ? 32 : 0));
    }

    public static BlockFacings from(final Direction... directions) {

        int mask = 0;

        for (final Direction direction : directions) {
            mask |= bit(direction);
        }

        return fromMask(mask);
    }

    public static BlockFacings from(final boolean[] facings) {

        int mask = 0;

        for (int i = 0; i < Math.min(6, facings.length); ++i) {
            if (facings[i]) {
                mask |= 1 << i;
            }
        }

        return fromMask(mask);
    }

    public boolean isSet(final Direction direction) {
        return 0 != (this._mask & bit(direction));
    }

    public boolean none() {
        return 0 == this._mask;
    }

    public boolean all() {
        return 0x3f == this._mask;
    }

    public boolean some() {
        return 0 != this._mask;
    }

    public boolean one() {
        return 1 == Integer.bitCount(this._mask);
    }

    public int countFacesIf(final boolean areSet) {
        return areSet ? Integer.bitCount(this._mask) : 6 - Integer.bitCount(this._mask);
    }

    /**
     * @return the first face (in {@link Direction} order) whose set-state matches {@code isSet}
     */
    public Optional<Direction> firstIf(final boolean isSet) {

        for (final Direction direction : Direction.values()) {
            if (this.isSet(direction) == isSet) {
                return Optional.of(direction);
            }
        }

        return Optional.empty();
    }

    public BlockFacings set(final Direction direction, final boolean value) {
        return fromMask(value ? this._mask | bit(direction) : this._mask & ~bit(direction));
    }

    public BlockFacings union(final BlockFacings other) {
        return fromMask(this._mask | other._mask);
    }

    public BlockFacings intersection(final BlockFacings other) {
        return fromMask(this._mask & other._mask);
    }

    public BlockFacings inverse() {
        return fromMask(~this._mask);
    }

    public int value() {
        return this._mask;
    }

    public BlockFacingsProperty toProperty() {
        return BlockFacingsProperty.fromMask(this._mask);
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof BlockFacings && ((BlockFacings) other)._mask == this._mask;
    }

    @Override
    public int hashCode() {
        return this._mask;
    }

    @Override
    public String toString() {

        final StringBuilder builder = new StringBuilder("Facings: ");

        for (final Direction direction : Direction.values()) {
            builder.append(this.isSet(direction) ? Character.toUpperCase(direction.getName().charAt(0)) : '-');
        }

        return builder.toString();
    }

    private static int bit(final Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    private BlockFacings(final int mask) {
        this._mask = mask;
    }

    private static final BlockFacings[] CACHE = new BlockFacings[64];

    static {

        for (int i = 0; i < CACHE.length; ++i) {
            CACHE[i] = new BlockFacings(i);
        }

        NONE = CACHE[0];
        ALL = CACHE[0x3f];
        DOWN = from(Direction.DOWN);
        UP = from(Direction.UP);
        NORTH = from(Direction.NORTH);
        SOUTH = from(Direction.SOUTH);
        WEST = from(Direction.WEST);
        EAST = from(Direction.EAST);
        VERTICAL = from(Direction.DOWN, Direction.UP);
        HORIZONTAL = from(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST);
        AXIS_X = from(Direction.WEST, Direction.EAST);
        AXIS_Y = VERTICAL;
        AXIS_Z = from(Direction.NORTH, Direction.SOUTH);
    }

    private final int _mask;
}
