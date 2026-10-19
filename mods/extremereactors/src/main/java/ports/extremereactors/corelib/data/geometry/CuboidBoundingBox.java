/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * The smallest box of whole blocks that contains a set of positions. Lengths and volumes count blocks, so a single
 * position has a length of one on every axis.
 * <p>
 * Boxes grow in place: {@link #add} and {@link #combine} change the receiver and return it. {@link #EMPTY} is the
 * only immutable box; adding to it returns a new box.
 */
public final class CuboidBoundingBox {

    public static final CuboidBoundingBox EMPTY = new CuboidBoundingBox(true);

    public CuboidBoundingBox() {
        this(false);
    }

    public CuboidBoundingBox(final BlockPos first, final BlockPos second) {

        this(false);
        this.add(first);
        this.add(second);
    }

    public CuboidBoundingBox add(final BlockPos position) {

        final CuboidBoundingBox target = this._immutable ? new CuboidBoundingBox() : this;

        if (target._empty) {

            target._minX = target._maxX = position.getX();
            target._minY = target._maxY = position.getY();
            target._minZ = target._maxZ = position.getZ();
            target._empty = false;

        } else {

            target._minX = Math.min(target._minX, position.getX());
            target._minY = Math.min(target._minY, position.getY());
            target._minZ = Math.min(target._minZ, position.getZ());
            target._maxX = Math.max(target._maxX, position.getX());
            target._maxY = Math.max(target._maxY, position.getY());
            target._maxZ = Math.max(target._maxZ, position.getZ());
        }

        return target;
    }

    public CuboidBoundingBox combine(final CuboidBoundingBox other) {

        if (other._empty) {
            return this;
        }

        return this.add(other.getMin()).add(other.getMax());
    }

    public boolean isEmpty() {
        return this._empty;
    }

    public BlockPos getMin() {
        return this._empty ? BlockPos.ZERO : new BlockPos(this._minX, this._minY, this._minZ);
    }

    public BlockPos getMax() {
        return this._empty ? BlockPos.ZERO : new BlockPos(this._maxX, this._maxY, this._maxZ);
    }

    public BlockPos getCenter() {
        return this._empty ? BlockPos.ZERO :
                new BlockPos((this._minX + this._maxX) / 2, (this._minY + this._maxY) / 2, (this._minZ + this._maxZ) / 2);
    }

    public int getLengthX() {
        return this._empty ? 0 : this._maxX - this._minX + 1;
    }

    public int getLengthY() {
        return this._empty ? 0 : this._maxY - this._minY + 1;
    }

    public int getLengthZ() {
        return this._empty ? 0 : this._maxZ - this._minZ + 1;
    }

    public int getVolume() {
        return this.getLengthX() * this.getLengthY() * this.getLengthZ();
    }

    /** The volume inside a one-block-thick shell. */
    public int getInternalVolume() {
        return Math.max(0, this.getLengthX() - 2) * Math.max(0, this.getLengthY() - 2) * Math.max(0, this.getLengthZ() - 2);
    }

    public boolean contains(final BlockPos position) {
        return !this._empty &&
                position.getX() >= this._minX && position.getX() <= this._maxX &&
                position.getY() >= this._minY && position.getY() <= this._maxY &&
                position.getZ() >= this._minZ && position.getZ() <= this._maxZ;
    }

    public AABB getAABB() {
        return this._empty ? new AABB(0, 0, 0, 0, 0, 0) :
                new AABB(this._minX, this._minY, this._minZ, this._maxX + 1, this._maxY + 1, this._maxZ + 1);
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || (other instanceof CuboidBoundingBox box && this._empty == box._empty &&
                (this._empty || (this.getMin().equals(box.getMin()) && this.getMax().equals(box.getMax()))));
    }

    @Override
    public int hashCode() {
        return this._empty ? 0 : 31 * this.getMin().hashCode() + this.getMax().hashCode();
    }

    @Override
    public String toString() {
        return this._empty ? "CuboidBoundingBox{empty}" : String.format("CuboidBoundingBox{(%d, %d, %d) - (%d, %d, %d)}",
                this._minX, this._minY, this._minZ, this._maxX, this._maxY, this._maxZ);
    }

    private CuboidBoundingBox(final boolean immutable) {

        this._immutable = immutable;
        this._empty = true;
    }

    private final boolean _immutable;
    private boolean _empty;
    private int _minX, _minY, _minZ, _maxX, _maxY, _maxZ;
}
