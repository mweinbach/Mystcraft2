/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Forge {@code FluidType} behavior the mixins apply: fluid heights, current pushing, fall distance, drowning,
 * swimming, boating and path types.
 */
public final class FluidPhysicsSupport {

    private FluidPhysicsSupport() {
    }

    /**
     * Forge's {@code Entity#isEyeInFluidType} for a predicate on the fluid: the eye block's fluid surface must be
     * above the eye. A boat the entity rides and is not under also hides it from the fluid.
     */
    public static boolean isEyeInFluid(final Entity entity, final Predicate<FluidProperties> predicate) {

        final Level level = entity.level;
        final double eyeLevel = entity.getEyeY() - 0.1111111119389534D;
        final Entity vehicle = entity.getVehicle();

        if (vehicle instanceof Boat boat && !boat.isUnderWater()
                && boat.getBoundingBox().maxY >= eyeLevel && boat.getBoundingBox().minY <= eyeLevel) {
            return false;
        }

        final BlockPos position = new BlockPos(entity.getX(), eyeLevel, entity.getZ());
        final FluidState state = level.getFluidState(position);
        final FluidProperties properties = FluidPhysics.propertiesOf(state);

        if (null == properties || !predicate.test(properties)) {
            return false;
        }

        return position.getY() + state.getHeight(level, position) > eyeLevel;
    }

    public static boolean isEyeInDrownableFluid(final Entity entity) {
        return isEyeInFluid(entity, FluidProperties::canDrown);
    }

    public static boolean isEyeInSwimmableFluid(final Entity entity) {
        return isEyeInFluid(entity, FluidProperties::canSwim);
    }

    public static boolean isEyeInBoatingFluid(final Entity entity) {
        return isEyeInFluid(entity, FluidProperties::supportsBoating);
    }

    /** Forge's {@code isInFluidType(state)} at the entity's feet, for any registered fluid. */
    public static boolean isInFluidAtFeet(final Entity entity) {

        final BlockPos position = entity.blockPosition();
        final FluidState state = entity.level.getFluidState(position);

        if (null == FluidPhysics.propertiesOf(state)) {
            return false;
        }

        return position.getY() + state.getHeight(entity.level, position) > entity.getBoundingBox().minY;
    }

    /** How deep any registered fluid is at the entity, as Forge tracks it, or 0 when it is not in one. */
    public static double fluidHeight(final Entity entity) {

        final double[] height = {0.0D};
        final AABB box = entity.getBoundingBox().inflate(0.001D);

        scan(entity.level, box, (position, state, properties) ->
                height[0] = Math.max(height[0], position.getY() + state.getHeight(entity.level, position) - box.minY));

        return height[0];
    }

    /** Whether the entity's body is in a fluid whose properties allow swimming. */
    public static boolean isInSwimmableFluid(final Entity entity) {

        final boolean[] found = {false};
        final AABB box = entity.getBoundingBox().inflate(0.001D);

        scan(entity.level, box, (position, state, properties) -> {
            if (properties.canSwim() && position.getY() + state.getHeight(entity.level, position) > box.minY) {
                found[0] = true;
            }
        });

        return found[0];
    }

    /**
     * Forge's per-fluid pass over an entity: it records the fluid heights, applies the fall distance modifier and
     * pushes entities with the fluid's flow. Runs after vanilla has handled water and lava.
     */
    public static void updateEntity(final Entity entity) {

        final Level level = entity.level;
        final AABB box = entity.getBoundingBox().inflate(0.001D);
        final Map<FluidProperties, Push> pushes = new IdentityHashMap<>();
        // Forge does not push a rider in a boat that is not under water when the fluid supports boating.
        final boolean ridingBoat = entity.getVehicle() instanceof Boat boat && !boat.isUnderWater();

        scan(level, box, (position, state, properties) -> {

            final double height = position.getY() + state.getHeight(level, position) - box.minY;

            if (height < 0.0D) {
                return;
            }

            // Forge skips fluids that support boating while riding a boat that is not under water.
            if (ridingBoat && properties.supportsBoating()) {
                return;
            }

            final Push push = pushes.computeIfAbsent(properties, ignored -> new Push());

            push.height = Math.max(push.height, height);

            // Forge's Entity#isPushedByFluid(FluidType) is isPushedByFluid() && fluidType.canPushEntity().
            if (properties.canPushEntity() && entity.isPushedByFluid()) {

                Vec3 flow = state.getFlow(level, position);

                if (push.height < 0.4D) {
                    flow = flow.scale(push.height);
                }

                push.flow = push.flow.add(flow);
                ++push.flowBlocks;
            }
        });

        if (pushes.isEmpty()) {
            return;
        }

        // Forge scales the fall distance by the smallest modifier when the entity is not riding a boat.
        if (!(entity.getVehicle() instanceof Boat)) {

            float modifier = 1.0f;

            for (final FluidProperties properties : pushes.keySet()) {
                modifier = Math.min(modifier, properties.fallDistanceModifier());
            }

            entity.fallDistance *= modifier;
        }

        for (final Map.Entry<FluidProperties, Push> entry : pushes.entrySet()) {

            final FluidProperties properties = entry.getKey();
            final Push push = entry.getValue();

            if (push.flow.lengthSqr() <= 0.0D) {
                continue;
            }

            Vec3 motion = push.flow;

            if (push.flowBlocks > 0) {
                motion = motion.scale(1.0D / (double) push.flowBlocks);
            }

            if (!(entity instanceof Player)) {
                motion = motion.normalize();
            }

            motion = motion.scale(properties.motionScale());

            final Vec3 current = entity.getDeltaMovement();

            if (Math.abs(current.x) < 0.003D && Math.abs(current.z) < 0.003D && motion.length() < 0.0045000000000000005D) {
                motion = motion.normalize().scale(0.0045000000000000005D);
            }

            entity.setDeltaMovement(current.add(motion));
        }
    }

    @Nullable
    public static BlockPathTypes pathType(final FluidState state) {

        final FluidProperties properties = FluidPhysics.propertiesOf(state);

        return null == properties ? null : properties.pathType();
    }

    public static boolean isWaterForBoating(final FluidState state, final TagKey<Fluid> tag) {

        final boolean vanilla = state.is(tag);

        if (!tag.equals(FluidTags.WATER)) {
            return vanilla;
        }

        // Vanilla water is not registered, so a registered-fluid-only answer would break ordinary boats.
        final FluidProperties properties = FluidPhysics.propertiesOf(state);

        return vanilla || (null != properties && properties.supportsBoating());
    }

    private static void scan(final Level level, final AABB box, final FluidConsumer consumer) {

        final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();

        for (int x = (int) Math.floor(box.minX); x < (int) Math.ceil(box.maxX); ++x) {
            for (int y = (int) Math.floor(box.minY); y < (int) Math.ceil(box.maxY); ++y) {
                for (int z = (int) Math.floor(box.minZ); z < (int) Math.ceil(box.maxZ); ++z) {

                    position.set(x, y, z);

                    final FluidState state = level.getFluidState(position);
                    final FluidProperties properties = FluidPhysics.propertiesOf(state);

                    if (null != properties) {
                        consumer.accept(position, state, properties);
                    }
                }
            }
        }
    }

    @FunctionalInterface
    private interface FluidConsumer {
        void accept(BlockPos position, FluidState state, FluidProperties properties);
    }

    private static final class Push {
        double height;
        Vec3 flow = Vec3.ZERO;
        int flowBlocks;
    }
}
