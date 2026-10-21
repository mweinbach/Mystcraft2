/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.level;

import net.minecraft.world.level.LevelAccessor;
import ports.mekanism.lib.eventbus.api.Event;

public class LevelEvent extends Event {

    private final LevelAccessor level;

    public LevelEvent(LevelAccessor level) {
        this.level = level;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public static class Load extends LevelEvent {

        public Load(LevelAccessor level) {
            super(level);
        }
    }

    public static class Unload extends LevelEvent {

        public Unload(LevelAccessor level) {
            super(level);
        }
    }

    public static class Save extends LevelEvent {

        public Save(LevelAccessor level) {
            super(level);
        }
    }
}
