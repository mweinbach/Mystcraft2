/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.patched;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * Forge's {@code ItemTags.create}, {@code BlockTags.create} and {@code FluidTags.create}.
 */
public final class ForgeTagKeys {

    private ForgeTagKeys() {
    }

    public static TagKey<Item> item(ResourceLocation name) {
        return TagKey.create(Registry.ITEM_REGISTRY, name);
    }

    public static TagKey<Block> block(ResourceLocation name) {
        return TagKey.create(Registry.BLOCK_REGISTRY, name);
    }

    public static TagKey<Fluid> fluid(ResourceLocation name) {
        return TagKey.create(Registry.FLUID_REGISTRY, name);
    }
}
