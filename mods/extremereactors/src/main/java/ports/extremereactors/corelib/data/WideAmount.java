/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A non-negative-or-negative quantity, usually energy, that can exceed the range of an {@code int}. Instances are
 * immutable: every operation returns a new value, so a reference taken earlier never changes behind the caller.
 * {@link #copy()}, {@link #toImmutable()} and {@link #asImmutable} exist so code written against a mutable API reads
 * naturally.
 */
public final class WideAmount implements Comparable<WideAmount> {

    public static final WideAmount ZERO = new WideAmount(0.0);
    public static final WideAmount ONE = new WideAmount(1.0);
    public static final WideAmount MAX_VALUE = new WideAmount(Double.MAX_VALUE);

    public static WideAmount from(final long value) {
        return from((double) value);
    }

    public static WideAmount from(final double value) {

        if (0.0 == value) {
            return ZERO;
        }

        return new WideAmount(value);
    }

    public static WideAmount asImmutable(final long value) {
        return from(value);
    }

    public static WideAmount asImmutable(final double value) {
        return from(value);
    }

    public static WideAmount from(final CompoundTag data, final String key) {
        return data.contains(key) ? from(data.getDouble(key)) : ZERO;
    }

    public static WideAmount from(final FriendlyByteBuf buffer) {
        return from(buffer.readDouble());
    }

    public static WideAmount min(final WideAmount a, final WideAmount b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static WideAmount max(final WideAmount a, final WideAmount b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    //region arithmetic

    public WideAmount add(final WideAmount other) {
        return other.isZero() ? this : from(this._value + other._value);
    }

    public WideAmount add(final double other) {
        return from(this._value + other);
    }

    public WideAmount subtract(final WideAmount other) {
        return other.isZero() ? this : from(this._value - other._value);
    }

    public WideAmount subtract(final double other) {
        return from(this._value - other);
    }

    public WideAmount multiply(final WideAmount other) {
        return from(this._value * other._value);
    }

    public WideAmount multiply(final double other) {
        return from(this._value * other);
    }

    public WideAmount divide(final WideAmount other) {
        return from(this._value / other._value);
    }

    public WideAmount divide(final double other) {
        return from(this._value / other);
    }

    public WideAmount min(final WideAmount other) {
        return min(this, other);
    }

    public WideAmount max(final WideAmount other) {
        return max(this, other);
    }

    public WideAmount negate() {
        return from(-this._value);
    }

    //endregion
    //region comparison

    public boolean isZero() {
        return 0.0 == this._value;
    }

    public boolean isPositive() {
        return this._value > 0.0;
    }

    public boolean greaterThan(final WideAmount other) {
        return this._value > other._value;
    }

    public boolean greaterOrEqual(final WideAmount other) {
        return this._value >= other._value;
    }

    public boolean smallerThan(final WideAmount other) {
        return this._value < other._value;
    }

    public boolean lessThan(final WideAmount other) {
        return this._value < other._value;
    }

    public boolean smallerOrEqual(final WideAmount other) {
        return this._value <= other._value;
    }

    @Override
    public int compareTo(final WideAmount other) {
        return Double.compare(this._value, other._value);
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || (other instanceof WideAmount amount && 0 == Double.compare(this._value, amount._value));
    }

    @Override
    public int hashCode() {
        return Double.hashCode(this._value);
    }

    //endregion
    //region conversion

    public WideAmount copy() {
        return this;
    }

    public WideAmount toImmutable() {
        return this;
    }

    public double doubleValue() {
        return this._value;
    }

    public long longValue() {
        return this._value >= (double) Long.MAX_VALUE ? Long.MAX_VALUE :
                this._value <= (double) Long.MIN_VALUE ? Long.MIN_VALUE : (long) this._value;
    }

    /** Clamped to the {@code int} range. */
    public int intValue() {
        return this._value >= Integer.MAX_VALUE ? Integer.MAX_VALUE :
                this._value <= Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) this._value;
    }

    /** This amount as a fraction of {@code total}, in the range 0 to 1; zero if {@code total} is zero. */
    public double percentage(final WideAmount total) {
        return total.isZero() ? 0.0 : Math.max(0.0, Math.min(1.0, this._value / total._value));
    }

    public CompoundTag serializeTo(final CompoundTag data, final String key) {

        data.putDouble(key, this._value);
        return data;
    }

    public void serializeTo(final FriendlyByteBuf buffer) {
        buffer.writeDouble(this._value);
    }

    @Override
    public String toString() {
        return this._value == Math.rint(this._value) && Math.abs(this._value) < 1e15 ?
                Long.toString((long) this._value) : Double.toString(this._value);
    }

    //endregion

    private WideAmount(final double value) {
        this._value = value;
    }

    private final double _value;
}
