/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.settings;

import net.minecraft.client.Minecraft;

public enum KeyConflictContext implements IKeyConflictContext {
    /**
     * Active everywhere; conflicts with every context.
     */
    UNIVERSAL {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return true;
        }
    },
    /**
     * Active while a screen is open.
     */
    GUI {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen != null;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    },
    /**
     * Active while no screen is open.
     */
    IN_GAME {
        @Override
        public boolean isActive() {
            return !GUI.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    }
}
