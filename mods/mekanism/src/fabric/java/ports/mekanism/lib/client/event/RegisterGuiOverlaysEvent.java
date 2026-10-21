/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import ports.mekanism.lib.client.gui.overlay.GuiOverlayManager;
import ports.mekanism.lib.client.gui.overlay.IGuiOverlay;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired once on the mod buses during client setup. Overlay ids are in the namespace of the bus' mod.
 */
public class RegisterGuiOverlaysEvent extends Event {

    private final String namespace;

    public RegisterGuiOverlaysEvent(String namespace) {
        this.namespace = namespace;
    }

    public void registerBelowAll(@NotNull String id, @NotNull IGuiOverlay overlay) {
        GuiOverlayManager.registerBelowAll(new ResourceLocation(namespace, id), overlay);
    }

    public void registerBelow(@NotNull ResourceLocation other, @NotNull String id, @NotNull IGuiOverlay overlay) {
        GuiOverlayManager.registerBelow(other, new ResourceLocation(namespace, id), overlay);
    }

    public void registerAbove(@NotNull ResourceLocation other, @NotNull String id, @NotNull IGuiOverlay overlay) {
        GuiOverlayManager.registerAbove(other, new ResourceLocation(namespace, id), overlay);
    }

    public void registerAboveAll(@NotNull String id, @NotNull IGuiOverlay overlay) {
        GuiOverlayManager.registerAboveAll(new ResourceLocation(namespace, id), overlay);
    }
}
