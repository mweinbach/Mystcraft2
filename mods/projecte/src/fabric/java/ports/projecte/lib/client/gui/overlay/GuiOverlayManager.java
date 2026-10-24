/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.gui.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import ports.projecte.lib.client.event.RenderGuiOverlayEvent;
import ports.projecte.lib.common.MinecraftForge;

/**
 * Orders modded HUD overlays among vanilla's HUD parts and draws them from the points in vanilla's HUD rendering
 * that mixins hook. Vanilla draws health, armor, food and air in one call, so overlays anchored inside that group are
 * all drawn after it.
 */
public final class GuiOverlayManager {

    public enum Hook {
        BEFORE_ALL,
        AFTER_HOTBAR,
        AFTER_PLAYER_HEALTH,
        AFTER_ALL
    }

    private static final List<ResourceLocation> ORDER = new ArrayList<>();
    private static final Map<ResourceLocation, NamedGuiOverlay> MODDED = new HashMap<>();
    private static final Map<ResourceLocation, VanillaGuiOverlay> VANILLA = new HashMap<>();
    private static Map<Hook, List<NamedGuiOverlay>> byHook;
    private static ForgeGui gui;
    private static float partialTick;

    static {
        for (VanillaGuiOverlay overlay : VanillaGuiOverlay.values()) {
            ORDER.add(overlay.id());
            VANILLA.put(overlay.id(), overlay);
        }
    }

    private GuiOverlayManager() {
    }

    public static synchronized void registerBelowAll(ResourceLocation id, IGuiOverlay overlay) {
        insert(0, id, overlay);
    }

    public static synchronized void registerBelow(ResourceLocation other, ResourceLocation id, IGuiOverlay overlay) {
        insert(indexOf(other), id, overlay);
    }

    public static synchronized void registerAbove(ResourceLocation other, ResourceLocation id, IGuiOverlay overlay) {
        insert(indexOf(other) + 1, id, overlay);
    }

    public static synchronized void registerAboveAll(ResourceLocation id, IGuiOverlay overlay) {
        insert(ORDER.size(), id, overlay);
    }

    private static synchronized void insert(int index, ResourceLocation id, IGuiOverlay overlay) {
        if (VANILLA.containsKey(id) || MODDED.putIfAbsent(id, new NamedGuiOverlay(id, overlay)) != null) {
            throw new IllegalStateException("HUD overlay already registered with id " + id);
        }
        ORDER.add(index, id);
        byHook = null;
    }

    private static synchronized int indexOf(ResourceLocation id) {
        int index = ORDER.indexOf(id);
        if (index == -1) {
            throw new IllegalArgumentException("Unknown HUD overlay " + id);
        }
        return index;
    }

    private static synchronized Map<Hook, List<NamedGuiOverlay>> byHook() {
        if (byHook == null) {
            Map<Hook, List<NamedGuiOverlay>> map = new EnumMap<>(Hook.class);
            for (Hook hook : Hook.values()) {
                map.put(hook, new ArrayList<>());
            }
            Hook current = Hook.BEFORE_ALL;
            for (ResourceLocation id : ORDER) {
                VanillaGuiOverlay vanilla = VANILLA.get(id);
                if (vanilla == null) {
                    map.get(current).add(MODDED.get(id));
                } else {
                    current = vanilla.nextHook;
                }
            }
            byHook = map;
        }
        return byHook;
    }

    /**
     * Posts the vanilla overlay event. Canceling the pre event means vanilla should skip that part of the HUD.
     */
    public static boolean preVanilla(VanillaGuiOverlay overlay, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        return MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Pre(minecraft.getWindow(), poseStack, partialTick, overlay.type()));
    }

    public static void postVanilla(VanillaGuiOverlay overlay, PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Post(minecraft.getWindow(), poseStack, partialTick, overlay.type()));
    }

    public static void render(Hook hook, PoseStack poseStack, float partialTick) {
        GuiOverlayManager.partialTick = partialTick;
        Minecraft minecraft = Minecraft.getInstance();
        if (gui == null) {
            gui = new ForgeGui(minecraft);
        }
        if (hook == Hook.BEFORE_ALL) {
            gui.leftHeight = 39;
            gui.rightHeight = 39;
        } else if (hook == Hook.AFTER_PLAYER_HEALTH) {
            updateHeights(minecraft);
        }
        List<NamedGuiOverlay> overlays = byHook().get(hook);
        if (overlays.isEmpty()) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        for (NamedGuiOverlay overlay : overlays) {
            if (!MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Pre(minecraft.getWindow(), poseStack, partialTick, overlay))) {
                overlay.overlay().render(gui, poseStack, partialTick, width, height);
                MinecraftForge.EVENT_BUS.post(new RenderGuiOverlayEvent.Post(minecraft.getWindow(), poseStack, partialTick, overlay));
            }
        }
    }

    /**
     * Recomputes the heights of the left and right status bar stacks the way vanilla lays out health, armor, food and
     * air.
     */
    private static void updateHeights(Minecraft minecraft) {
        gui.leftHeight = 39;
        gui.rightHeight = 39;
        if (!gui.shouldDrawSurvivalElements() || !(minecraft.getCameraEntity() instanceof Player player)) {
            return;
        }
        float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), Mth.ceil(player.getHealth()));
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int healthRows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        gui.leftHeight += (healthRows - 1) * rowHeight + 10;
        if (player.getArmorValue() > 0) {
            gui.leftHeight += 10;
        }
        if (!(player.getVehicle() instanceof LivingEntity vehicle) || !vehicle.showVehicleHealth()) {
            gui.rightHeight += 10;
        }
        if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply()) {
            gui.rightHeight += 10;
        }
    }
}
