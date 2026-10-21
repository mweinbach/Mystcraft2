/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import ports.mekanism.lib.eventbus.api.Cancelable;
import ports.mekanism.lib.eventbus.api.Event;

public abstract class InputEvent extends Event {

    protected InputEvent() {
    }

    /**
     * Fired when the mouse wheel scrolls in game, with no screen open. Canceling it stops vanilla's hotbar scrolling.
     */
    @Cancelable
    public static class MouseScrollingEvent extends InputEvent {

        private final double scrollDelta;
        private final double mouseX;
        private final double mouseY;
        private final boolean leftDown;
        private final boolean middleDown;
        private final boolean rightDown;

        public MouseScrollingEvent(double scrollDelta, boolean leftDown, boolean middleDown, boolean rightDown, double mouseX, double mouseY) {
            this.scrollDelta = scrollDelta;
            this.leftDown = leftDown;
            this.middleDown = middleDown;
            this.rightDown = rightDown;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        public double getScrollDelta() {
            return scrollDelta;
        }

        public boolean isLeftDown() {
            return leftDown;
        }

        public boolean isRightDown() {
            return rightDown;
        }

        public boolean isMiddleDown() {
            return middleDown;
        }

        public double getMouseX() {
            return mouseX;
        }

        public double getMouseY() {
            return mouseY;
        }
    }
}
