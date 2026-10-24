/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.gui.overlay;

import net.minecraft.resources.ResourceLocation;

/**
 * The parts of vanilla's HUD, in Forge's drawing order. Vanilla still draws them itself; these entries are anchors for
 * modded overlays and the identity of {@code RenderGuiOverlayEvent}s. Each names the first point in vanilla's HUD
 * rendering after it where modded overlays can be drawn.
 */
public enum VanillaGuiOverlay {
    VIGNETTE("vignette", GuiOverlayManager.Hook.AFTER_HOTBAR),
    SPYGLASS("spyglass", GuiOverlayManager.Hook.AFTER_HOTBAR),
    HELMET("helmet", GuiOverlayManager.Hook.AFTER_HOTBAR),
    FROSTBITE("frostbite", GuiOverlayManager.Hook.AFTER_HOTBAR),
    PORTAL("portal", GuiOverlayManager.Hook.AFTER_HOTBAR),
    HOTBAR("hotbar", GuiOverlayManager.Hook.AFTER_HOTBAR),
    CROSSHAIR("crosshair", GuiOverlayManager.Hook.AFTER_PLAYER_HEALTH),
    BOSS_EVENT_PROGRESS("boss_event_progress", GuiOverlayManager.Hook.AFTER_PLAYER_HEALTH),
    PLAYER_HEALTH("player_health", GuiOverlayManager.Hook.AFTER_PLAYER_HEALTH),
    ARMOR_LEVEL("armor_level", GuiOverlayManager.Hook.AFTER_PLAYER_HEALTH),
    FOOD_LEVEL("food_level", GuiOverlayManager.Hook.AFTER_PLAYER_HEALTH),
    MOUNT_HEALTH("mount_health", GuiOverlayManager.Hook.AFTER_ALL),
    AIR_LEVEL("air_level", GuiOverlayManager.Hook.AFTER_PLAYER_HEALTH),
    JUMP_BAR("jump_bar", GuiOverlayManager.Hook.AFTER_ALL),
    EXPERIENCE_BAR("experience_bar", GuiOverlayManager.Hook.AFTER_ALL),
    ITEM_NAME("item_name", GuiOverlayManager.Hook.AFTER_ALL),
    SLEEP_FADE("sleep_fade", GuiOverlayManager.Hook.AFTER_ALL),
    DEBUG_TEXT("debug_text", GuiOverlayManager.Hook.AFTER_ALL),
    FPS_GRAPH("fps_graph", GuiOverlayManager.Hook.AFTER_ALL),
    POTION_ICONS("potion_icons", GuiOverlayManager.Hook.AFTER_ALL),
    RECORD_OVERLAY("record_overlay", GuiOverlayManager.Hook.AFTER_ALL),
    SUBTITLES("subtitles", GuiOverlayManager.Hook.AFTER_ALL),
    TITLE_TEXT("title_text", GuiOverlayManager.Hook.AFTER_ALL),
    SCOREBOARD("scoreboard", GuiOverlayManager.Hook.AFTER_ALL),
    CHAT_PANEL("chat_panel", GuiOverlayManager.Hook.AFTER_ALL),
    PLAYER_LIST("player_list", GuiOverlayManager.Hook.AFTER_ALL);

    private final ResourceLocation id;
    private final NamedGuiOverlay type;
    final GuiOverlayManager.Hook nextHook;

    VanillaGuiOverlay(String id, GuiOverlayManager.Hook nextHook) {
        this.id = new ResourceLocation(id);
        this.type = new NamedGuiOverlay(this.id, (gui, poseStack, partialTick, screenWidth, screenHeight) -> {
        });
        this.nextHook = nextHook;
    }

    public ResourceLocation id() {
        return id;
    }

    public NamedGuiOverlay type() {
        return type;
    }
}
