/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The registry of fluids that get Forge {@code FluidType}-style entity physics on Fabric.
 *
 * <p>Register both the still and the flowing fluid during mod initialization. The mixins look fluids up by
 * {@link FluidState#getType()}, so a state backed by the flowing instance needs its own registration too.</p>
 */
public final class FluidPhysics {

    private static volatile Map<Fluid, FluidProperties> s_registry = Map.of();

    private FluidPhysics() {
    }

    /**
     * Register a still and flowing fluid with the same properties.
     */
    public static void register(final Fluid still, final Fluid flowing, final FluidProperties properties) {

        Objects.requireNonNull(still, "still");
        Objects.requireNonNull(flowing, "flowing");

        if (still == flowing) {
            throw new IllegalArgumentException("still and flowing must be different fluids");
        }

        final FluidProperties snapshot = Objects.requireNonNull(properties, "properties").snapshot();

        synchronized (FluidPhysics.class) {
            final Map<Fluid, FluidProperties> next = new HashMap<>(s_registry);

            put(next, still, snapshot);
            put(next, flowing, snapshot);
            s_registry = Map.copyOf(next);
        }
    }

    /**
     * Register one fluid instance. Use {@link #register(Fluid, Fluid, FluidProperties)} for normal still/flowing pairs.
     */
    public static void register(final Fluid fluid, final FluidProperties properties) {

        Objects.requireNonNull(fluid, "fluid");

        final FluidProperties snapshot = Objects.requireNonNull(properties, "properties").snapshot();

        synchronized (FluidPhysics.class) {
            final Map<Fluid, FluidProperties> next = new HashMap<>(s_registry);

            put(next, fluid, snapshot);
            s_registry = Map.copyOf(next);
        }
    }

    /**
     * The properties of one of our fluids, or null for an unregistered fluid.
     */
    @Nullable
    public static FluidProperties propertiesOf(final Fluid fluid) {
        return s_registry.get(fluid);
    }

    /**
     * The properties of the fluid backing a state, or null for an empty state or an unregistered fluid.
     */
    @Nullable
    public static FluidProperties propertiesOf(final FluidState state) {

        if (state.isEmpty()) {
            return null;
        }

        return s_registry.get(state.getType());
    }

    /** Whether no fluid is registered, so the mixins can skip their work. */
    static boolean isEmpty() {
        return s_registry.isEmpty();
    }

    private static void put(final Map<Fluid, FluidProperties> registry, final Fluid fluid,
                            final FluidProperties properties) {

        if (fluid == Fluids.EMPTY) {
            throw new IllegalArgumentException("the empty fluid cannot have physics properties");
        }

        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER
                || fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) {
            throw new IllegalArgumentException("vanilla fluids already have their own physics");
        }

        if (null != registry.putIfAbsent(fluid, properties)) {
            throw new IllegalStateException("fluid " + fluid + " already has physics properties");
        }
    }
}
