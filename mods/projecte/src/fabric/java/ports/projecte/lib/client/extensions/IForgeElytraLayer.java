/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions;

import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The methods Forge adds to {@link ElytraLayer} so subclasses can draw other chest items as wings, injected into it on
 * Fabric. A mixin makes the layer's rendering use them.
 */
public interface IForgeElytraLayer<T extends LivingEntity> {

    default boolean shouldRender(ItemStack stack, T entity) {
        return stack.is(Items.ELYTRA);
    }

    default ResourceLocation getElytraTexture(ItemStack stack, T entity) {
        return ElytraLayer.WINGS_LOCATION;
    }
}
