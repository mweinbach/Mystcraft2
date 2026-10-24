/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.entity.player;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import ports.projecte.lib.eventbus.api.Cancelable;

/**
 * Posted when a player touches an item entity it may pick up, before vanilla adds the stack to the inventory.
 * Canceling it leaves the pickup to the listener.
 */
@Cancelable
public class EntityItemPickupEvent extends PlayerEvent {

    private final ItemEntity item;

    public EntityItemPickupEvent(Player player, ItemEntity item) {
        super(player);
        this.item = item;
    }

    public ItemEntity getItem() {
        return item;
    }

    @Override
    public boolean isCancelable() {
        return true;
    }
}
