/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/**
 * Forge side of the bucket base: the fluid is resolved lazily, as Forge may register items before fluids.
 * The Fabric source set has a class with the same name and API.
 */
public class PlatformBucketItem extends BucketItem {

    public PlatformBucketItem(final Supplier<? extends Fluid> fluid, final Properties properties) {
        super(fluid, properties);
    }
}
