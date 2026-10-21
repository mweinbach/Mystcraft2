/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.gui.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The HUD state handed to modded overlays. Unlike Forge's it wraps vanilla's {@link Gui} instead of replacing it;
 * {@link #leftHeight} and {@link #rightHeight} are derived from what vanilla drew before the overlay.
 */
public class ForgeGui {

    private final Minecraft minecraft;
    public int leftHeight = 39;
    public int rightHeight = 39;

    public ForgeGui(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public Minecraft getMinecraft() {
        return minecraft;
    }

    public Gui getVanillaGui() {
        return minecraft.gui;
    }

    public Font getFont() {
        return minecraft.font;
    }

    public void setupOverlayRenderState(boolean blend, boolean depthTest) {
        setupOverlayRenderState(blend, depthTest, GuiComponent.GUI_ICONS_LOCATION);
    }

    public void setupOverlayRenderState(boolean blend, boolean depthTest, @Nullable ResourceLocation texture) {
        if (blend) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        if (texture != null) {
            RenderSystem.enableTexture();
            RenderSystem.setShaderTexture(0, texture);
        } else {
            RenderSystem.disableTexture();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }

    public boolean shouldDrawSurvivalElements() {
        return minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer() && minecraft.getCameraEntity() instanceof Player;
    }
}
