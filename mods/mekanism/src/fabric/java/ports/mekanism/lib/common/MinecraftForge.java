/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.level.Level;
import ports.mekanism.lib.event.level.BlockEvent;
import ports.mekanism.lib.eventbus.api.Event;
import ports.mekanism.lib.eventbus.api.EventBus;
import ports.mekanism.lib.eventbus.api.EventListener;
import ports.mekanism.lib.eventbus.api.EventPriority;
import ports.mekanism.lib.eventbus.api.IEventBus;

public final class MinecraftForge {

    /**
     * The game event bus. The Fabric adapter fires the port's game events on it from Fabric API callbacks and mixins.
     */
    public static final IEventBus EVENT_BUS = new GameBus(new EventBus("forge"));

    private MinecraftForge() {
    }

    /**
     * Posts a break event the loader itself raised from {@link PlayerBlockBreakEvents#BEFORE}, without offering it
     * back to that callback.
     */
    public static boolean postLoaderBreakEvent(BlockEvent.BreakEvent event) {
        return ((GameBus) EVENT_BUS).delegate().post(event);
    }

    /**
     * Mekanism posts {@link BlockEvent.BreakEvent} itself to ask whether a machine or tool may break a block. On
     * Fabric, protection mods answer through {@link PlayerBlockBreakEvents#BEFORE}, so a break event is also offered
     * there.
     */
    private record GameBus(EventBus delegate) implements IEventBus {

        @Override
        public void register(Object target) {
            delegate.register(target);
        }

        @Override
        public <T extends Event> void addListener(EventListener<T> consumer) {
            delegate.addListener(consumer);
        }

        @Override
        public <T extends Event> void addListener(EventPriority priority, EventListener<T> consumer) {
            delegate.addListener(priority, consumer);
        }

        @Override
        public <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, EventListener<T> consumer) {
            delegate.addListener(priority, receiveCancelled, consumer);
        }

        @Override
        public <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType, Consumer<T> consumer) {
            delegate.addListener(priority, receiveCancelled, eventType, consumer);
        }

        @Override
        public boolean post(Event event) {
            if (event instanceof BlockEvent.BreakEvent breakEvent && breakEvent.getLevel() instanceof Level level && !level.isClientSide) {
                boolean allowed = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, breakEvent.getPlayer(), breakEvent.getPos(), breakEvent.getState(),
                      level.getBlockEntity(breakEvent.getPos()));
                if (!allowed) {
                    breakEvent.setCanceled(true);
                }
            }
            return delegate.post(event);
        }
    }
}
