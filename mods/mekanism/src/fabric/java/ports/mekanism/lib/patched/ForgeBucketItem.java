/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.patched;

import java.util.function.Supplier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;

/**
 * Forge's {@code BucketItem(Supplier, Properties)}. The port registers fluids before items, so the fluid exists when
 * the bucket is created.
 */
public class ForgeBucketItem extends BucketItem {

    public ForgeBucketItem(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid.get(), properties);
    }
}
