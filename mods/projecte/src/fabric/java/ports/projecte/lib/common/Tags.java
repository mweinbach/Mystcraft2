/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * The Forge common tags the port reads. Ore tags use Fabric's {@code c:} names. Lapis uses {@code forge:gems/lapis},
 * which the Fabric tag bridge fills with lapis lazuli.
 */
public final class Tags {

    private Tags() {
    }

    public static final class Items {

        public static final TagKey<Item> ORES_IRON = tag("iron_ores");
        public static final TagKey<Item> ORES_GOLD = tag("gold_ores");
        public static final TagKey<Item> ORES_COPPER = tag("copper_ores");
        /** Forge's {@code forge:gems/lapis}. The Fabric bridge lists lapis lazuli; {@code c:lapis} is not generated. */
        public static final TagKey<Item> GEMS_LAPIS = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("forge", "gems/lapis"));
        /** Forge's {@code forge:ores} and {@code forge:raw_materials}; the Fabric bridges add {@code c:ores} and {@code c:raw_ores}. */
        public static final TagKey<Item> ORES = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("forge", "ores"));
        public static final TagKey<Item> RAW_MATERIALS = TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("forge", "raw_materials"));

        private Items() {
        }

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("c", name));
        }
    }

    public static final class Blocks {

        /** Forge's {@code forge:gravel} and {@code forge:ores}, bridged to the vanilla blocks and Fabric's {@code c:} tags. */
        public static final TagKey<Block> GRAVEL = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("forge", "gravel"));
        public static final TagKey<Block> ORES = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("forge", "ores"));

        private Blocks() {
        }
    }

    public static final class Fluids {

        public static final TagKey<Fluid> GASEOUS = TagKey.create(Registry.FLUID_REGISTRY, new ResourceLocation("c", "gaseous"));

        private Fluids() {
        }
    }
}
