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
        assertEquals(BlockPathTypes.WATER, properties.pathType());
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
                .pathType(null);

        assertEquals(0.02D, properties.motionScale());
        assertFalse(properties.canPushEntity());
        assertFalse(properties.canSwim());
        assertFalse(properties.canDrown());
        assertEquals(0.1F, properties.fallDistanceModifier());
        assertTrue(properties.supportsBoating());
        assertNull(properties.pathType());
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
