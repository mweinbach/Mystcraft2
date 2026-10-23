/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Forge side of the bucket base: the fluid is resolved lazily, as Forge may register items before fluids.
 * The Fabric source set has a class with the same name and API.
 */
public class PlatformBucketItem extends BucketItem {

    public PlatformBucketItem(final Supplier<? extends Fluid> fluid, final Properties properties) {
        super(fluid, properties);
    }

    /**
     * Forge gives only {@link BucketItem} itself, not its subclasses, the {@link FluidBucketWrapper} other mods' tanks
     * and pipes drain and fill buckets through. Upstream registers plain bucket items, so it has the wrapper too.
     */
    @Override
    public ICapabilityProvider initCapabilities(final ItemStack stack, @Nullable final CompoundTag nbt) {
        return new FluidBucketWrapper(stack);
    }
}
