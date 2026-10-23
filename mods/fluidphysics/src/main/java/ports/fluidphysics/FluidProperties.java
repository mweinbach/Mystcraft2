/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics;

import net.minecraft.world.level.pathfinder.BlockPathTypes;

import javax.annotation.Nullable;

/**
 * The per-fluid physics Forge exposes through {@code FluidType.Properties}. The builder names and defaults follow
 * Forge 1.19.2, so a Forge port can map its existing fluid type values onto this class.
 *
 * <p>{@link FluidPhysics#register} takes a snapshot. Keep using the builder afterwards if convenient; changes do not
 * affect a fluid that was already registered.</p>
 */
public final class FluidProperties {

    private double motionScale = 0.014D;
    private boolean canPushEntity = true;
    private boolean canSwim = true;
    private boolean canDrown = true;
    private float fallDistanceModifier = 0.5F;
    private boolean supportsBoating;
    @Nullable
    private BlockPathTypes pathType = BlockPathTypes.WATER;

    private FluidProperties() {
    }

    public static FluidProperties create() {
        return new FluidProperties();
    }

    public FluidProperties motionScale(final double motionScale) {

        if (!Double.isFinite(motionScale) || motionScale < 0.0D) {
            throw new IllegalArgumentException("motionScale must be a finite, non-negative number");
        }

        this.motionScale = motionScale;
        return this;
    }

    public FluidProperties canPushEntity(final boolean canPushEntity) {
        this.canPushEntity = canPushEntity;
        return this;
    }

    public FluidProperties canSwim(final boolean canSwim) {
        this.canSwim = canSwim;
        return this;
    }

    public FluidProperties canDrown(final boolean canDrown) {
        this.canDrown = canDrown;
        return this;
    }

    public FluidProperties fallDistanceModifier(final float fallDistanceModifier) {

        if (!Float.isFinite(fallDistanceModifier) || fallDistanceModifier < 0.0F) {
            throw new IllegalArgumentException("fallDistanceModifier must be a finite, non-negative number");
        }

        this.fallDistanceModifier = fallDistanceModifier;
        return this;
    }

    public FluidProperties supportsBoating(final boolean supportsBoating) {
        this.supportsBoating = supportsBoating;
        return this;
    }

    public FluidProperties pathType(@Nullable final BlockPathTypes pathType) {
        this.pathType = pathType;
        return this;
    }

    public double motionScale() {
        return this.motionScale;
    }

    public boolean canPushEntity() {
        return this.canPushEntity;
    }

    public boolean canSwim() {
        return this.canSwim;
    }

    public boolean canDrown() {
        return this.canDrown;
    }

    public float fallDistanceModifier() {
        return this.fallDistanceModifier;
    }

    public boolean supportsBoating() {
        return this.supportsBoating;
    }

    @Nullable
    public BlockPathTypes pathType() {
        return this.pathType;
    }

    FluidProperties snapshot() {

        final FluidProperties copy = new FluidProperties();

        copy.motionScale = this.motionScale;
        copy.canPushEntity = this.canPushEntity;
        copy.canSwim = this.canSwim;
        copy.canDrown = this.canDrown;
        copy.fallDistanceModifier = this.fallDistanceModifier;
        copy.supportsBoating = this.supportsBoating;
        copy.pathType = this.pathType;
        return copy;
    }
}
