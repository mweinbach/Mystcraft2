/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import net.minecraft.world.level.ItemLike;
import ports.mekanism.lib.client.IItemDecorator;
import ports.mekanism.lib.client.ItemDecoratorHandler;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired once on the mod buses during client setup.
 */
public class RegisterItemDecorationsEvent extends Event {

    public void register(ItemLike itemLike, IItemDecorator decorator) {
        ItemDecoratorHandler.register(itemLike.asItem(), decorator);
    }
}
