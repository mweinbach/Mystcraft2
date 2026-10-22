/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.eventbus.api.Cancelable;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * The slice of Forge's {@code ScreenEvent} the JEI screen hand-off uses.
 */
public class ScreenEvent extends Event {

    @Nullable
    private final Screen screen;

    public ScreenEvent(@Nullable Screen screen) {
        this.screen = screen;
    }

    @Nullable
    public Screen getScreen() {
        return screen;
    }

    @Cancelable
    public static class Opening extends ScreenEvent {

        @Nullable
        private Screen newScreen;

        public Opening(@Nullable Screen currentScreen, @Nullable Screen newScreen) {
            super(currentScreen);
            this.newScreen = newScreen;
        }

        @Nullable
        public Screen getCurrentScreen() {
            return getScreen();
        }

        @Nullable
        public Screen getNewScreen() {
            return newScreen;
        }

        public void setNewScreen(@Nullable Screen newScreen) {
            this.newScreen = newScreen;
        }
    }
}
