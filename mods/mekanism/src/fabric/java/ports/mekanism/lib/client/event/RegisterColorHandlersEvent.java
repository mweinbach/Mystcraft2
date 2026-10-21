/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.level.ItemLike;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired once per kind on the mod buses during client setup; handlers go to Fabric's color provider registry, which
 * applies them once the color tables exist.
 */
public abstract class RegisterColorHandlersEvent extends Event {

    protected RegisterColorHandlersEvent() {
    }

    public static class Block extends RegisterColorHandlersEvent {

        public void register(BlockColor blockColor, net.minecraft.world.level.block.Block... blocks) {
            ColorProviderRegistry.BLOCK.register(blockColor, blocks);
        }
    }

    public static class Item extends RegisterColorHandlersEvent {

        public void register(ItemColor itemColor, ItemLike... items) {
            ColorProviderRegistry.ITEM.register(itemColor, items);
        }
    }
}
