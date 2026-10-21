/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects;

/**
 * The insertion of one control rod as drawn, from 0 (withdrawn) to 1 (fully inserted). It travels toward the
 * rod's real insertion at a fixed stroke speed, or falls under gravity and bounces when the reactor is SCRAMmed.
 */
public final class RodDrive {

    /** Insertion travelled per tick in normal operation: a full stroke takes a second. */
    public static final float STROKE_PER_TICK = 0.05f;
    public static final float GRAVITY = 0.02f;
    public static final float BOUNCE = 0.3f;
    public static final float SETTLE_SPEED = 0.02f;

    public RodDrive(final float insertion) {
        this._position = this._previous = insertion;
    }

    public void tick(final float target, final boolean dropping) {

        this._previous = this._position;

        if (dropping && (this._position < target || 0f != this._velocity)) {

            this._velocity += GRAVITY;
            this._position += this._velocity;

            if (this._position >= target) {

                this._position = target;
                this._velocity = this._velocity > SETTLE_SPEED ? -this._velocity * BOUNCE : 0f;
            }

        } else {

            final float delta = target - this._position;

            this._velocity = 0f;
            this._position += Math.copySign(Math.min(Math.abs(delta), STROKE_PER_TICK), delta);
        }
    }

    public float get() {
        return this._position;
    }

    public float get(final float partialTicks) {
        return this._previous + (this._position - this._previous) * partialTicks;
    }

    public boolean isMoving() {
        return this._previous != this._position || 0f != this._velocity;
    }

    private float _position;
    private float _previous;
    private float _velocity;
}
