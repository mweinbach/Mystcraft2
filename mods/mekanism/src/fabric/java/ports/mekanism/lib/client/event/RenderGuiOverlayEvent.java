/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import ports.mekanism.lib.client.gui.overlay.NamedGuiOverlay;
import ports.mekanism.lib.eventbus.api.Cancelable;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired around each modded HUD overlay, and before vanilla's crosshair.
 */
public abstract class RenderGuiOverlayEvent extends Event {

    private final Window window;
    private final PoseStack poseStack;
    private final float partialTick;
    private final NamedGuiOverlay overlay;

    protected RenderGuiOverlayEvent(Window window, PoseStack poseStack, float partialTick, NamedGuiOverlay overlay) {
        this.window = window;
        this.poseStack = poseStack;
        this.partialTick = partialTick;
        this.overlay = overlay;
    }

    public Window getWindow() {
        return window;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public NamedGuiOverlay getOverlay() {
        return overlay;
    }

    @Cancelable
    public static class Pre extends RenderGuiOverlayEvent {

        public Pre(Window window, PoseStack poseStack, float partialTick, NamedGuiOverlay overlay) {
            super(window, poseStack, partialTick, overlay);
        }
    }

    public static class Post extends RenderGuiOverlayEvent {

        public Post(Window window, PoseStack poseStack, float partialTick, NamedGuiOverlay overlay) {
            super(window, poseStack, partialTick, overlay);
        }
    }
}
