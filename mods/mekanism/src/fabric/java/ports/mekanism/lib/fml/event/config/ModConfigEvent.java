/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fml.event.config;

import ports.mekanism.lib.eventbus.api.Event;
import ports.mekanism.lib.fml.config.ModConfig;

public class ModConfigEvent extends Event {

    private final ModConfig config;

    protected ModConfigEvent(ModConfig config) {
        this.config = config;
    }

    public ModConfig getConfig() {
        return config;
    }

    public static class Loading extends ModConfigEvent {

        public Loading(ModConfig config) {
            super(config);
        }
    }

    public static class Reloading extends ModConfigEvent {

        public Reloading(ModConfig config) {
            super(config);
        }
    }

    public static class Unloading extends ModConfigEvent {

        public Unloading(ModConfig config) {
            super(config);
        }
    }
}
