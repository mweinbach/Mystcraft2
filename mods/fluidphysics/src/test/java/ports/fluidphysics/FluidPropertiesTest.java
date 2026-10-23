/*
 * Fluid Physics - Fabric fluid physics library for Minecraft 1.19.2
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.fluidphysics;

import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidPropertiesTest {

    @Test
    void defaultsMatchForgeFluidTypeProperties() {

        final FluidProperties properties = FluidProperties.create();

        assertEquals(0.014D, properties.motionScale());
        assertTrue(properties.canPushEntity());
        assertTrue(properties.canSwim());
        assertTrue(properties.canDrown());
        assertEquals(0.5F, properties.fallDistanceModifier());
        assertFalse(properties.supportsBoating());
        assertFalse(properties.canExtinguish());
        assertEquals(BlockPathTypes.WATER, properties.pathType());
        assertEquals(BlockPathTypes.WATER_BORDER, properties.adjacentPathType());
    }

    @Test
    void builderChangesEveryProperty() {

        final FluidProperties properties = FluidProperties.create()
                .motionScale(0.02D)
                .canPushEntity(false)
                .canSwim(false)
                .canDrown(false)
                .fallDistanceModifier(0.1F)
                .supportsBoating(true)
                .canExtinguish(true)
                .pathType(null)
                .adjacentPathType(BlockPathTypes.DANGER_OTHER);

        assertEquals(0.02D, properties.motionScale());
        assertFalse(properties.canPushEntity());
        assertFalse(properties.canSwim());
        assertFalse(properties.canDrown());
        assertEquals(0.1F, properties.fallDistanceModifier());
        assertTrue(properties.supportsBoating());
        assertTrue(properties.canExtinguish());
        assertNull(properties.pathType());
        assertEquals(BlockPathTypes.DANGER_OTHER, properties.adjacentPathType());
        assertNull(FluidProperties.create().adjacentPathType(null).adjacentPathType());
    }

    @Test
    void snapshotCopiesEveryProperty() {

        final FluidProperties properties = FluidProperties.create()
                .motionScale(0.02D)
                .canPushEntity(false)
                .canSwim(false)
                .canDrown(false)
                .fallDistanceModifier(0.1F)
                .supportsBoating(true)
                .canExtinguish(true)
                .pathType(BlockPathTypes.LAVA)
                .adjacentPathType(null);
        final FluidProperties snapshot = properties.snapshot();

        properties.motionScale(0.5D).canPushEntity(true).canSwim(true).canDrown(true).fallDistanceModifier(0.9F)
                .supportsBoating(false).canExtinguish(false).pathType(BlockPathTypes.WATER)
                .adjacentPathType(BlockPathTypes.WATER_BORDER);

        assertEquals(0.02D, snapshot.motionScale());
        assertFalse(snapshot.canPushEntity());
        assertFalse(snapshot.canSwim());
        assertFalse(snapshot.canDrown());
        assertEquals(0.1F, snapshot.fallDistanceModifier());
        assertTrue(snapshot.supportsBoating());
        assertTrue(snapshot.canExtinguish());
        assertEquals(BlockPathTypes.LAVA, snapshot.pathType());
        assertNull(snapshot.adjacentPathType());
    }

    @Test
    void rejectsInvalidMotionValues() {
        assertThrows(IllegalArgumentException.class, () -> FluidProperties.create().motionScale(-1.0D));
        assertThrows(IllegalArgumentException.class, () -> FluidProperties.create().motionScale(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> FluidProperties.create().fallDistanceModifier(-0.1F));
        assertThrows(IllegalArgumentException.class,
                () -> FluidProperties.create().fallDistanceModifier(Float.POSITIVE_INFINITY));
    }
}
