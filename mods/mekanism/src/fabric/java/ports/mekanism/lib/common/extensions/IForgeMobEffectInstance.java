/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Forge's curative items for {@link net.minecraft.world.effect.MobEffectInstance}, injected into it on Fabric. Fabric
 * has no per-effect cure list, so milk, the only cure vanilla knows, cures every effect.
 */
public interface IForgeMobEffectInstance {

    default boolean isCurativeItem(ItemStack stack) {
        return stack.is(Items.MILK_BUCKET);
    }
}
