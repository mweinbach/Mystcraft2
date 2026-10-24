package com.agricraft.agricraft.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * The drawing calls AgriCraft's screens, journal page drawers and overlay make, on 1.19.2's {@link PoseStack} and
 * {@link GuiComponent} API. Upstream AgriCraft 4 draws through 1.20's {@code GuiGraphics}, which 1.19.2 lacks;
 * this port-specific class offers the subset of operations upstream uses, with the same argument order, so the
 * drawing code keeps its upstream shape. It is also the drawing context of the public
 * {@link com.agricraft.agricraft.api.tools.journal.JournalPageDrawer} API on 1.19.2.
 */
public final class AgriGuiGraphics {

	private final PoseStack pose;

	public AgriGuiGraphics(PoseStack pose) {
		this.pose = pose;
	}

	public PoseStack pose() {
		return this.pose;
	}

	/** A 256 x 256 texture region at its own size. */
	public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
		this.blit(texture, x, y, u, v, width, height, 256, 256);
	}

	/** A region of a {@code textureWidth} x {@code textureHeight} texture at its own size. */
	public void blit(ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		GuiComponent.blit(this.pose, x, y, u, v, width, height, textureWidth, textureHeight);
	}

	/** A {@code uWidth} x {@code vHeight} texture region stretched over {@code width} x {@code height}. */
	public void blit(ResourceLocation texture, int x, int y, int width, int height, float u, float v, int uWidth, int vHeight, int textureWidth, int textureHeight) {
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		GuiComponent.blit(this.pose, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
	}

	/** An atlas sprite. */
	public void blit(int x, int y, int z, int width, int height, TextureAtlasSprite sprite) {
		RenderSystem.setShaderTexture(0, sprite.atlas().location());
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		GuiComponent.blit(this.pose, x, y, z, width, height, sprite);
	}

	public int drawString(Font font, Component text, int x, int y, int color) {
		return this.drawString(font, text, x, y, color, true);
	}

	public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
		return shadow ? font.drawShadow(this.pose, text, x, y, color) : font.draw(this.pose, text, x, y, color);
	}

	public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
		return shadow ? font.drawShadow(this.pose, text, x, y, color) : font.draw(this.pose, text, x, y, color);
	}

	public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
		return shadow ? font.drawShadow(this.pose, text, x, y, color) : font.draw(this.pose, text, x, y, color);
	}

	/** Wrapped text in the current pose (1.19.2's {@code Font#drawWordWrap} ignores the pose stack). */
	public void drawWordWrap(Font font, FormattedText text, int x, int y, int lineWidth, int color) {
		for (FormattedCharSequence line : font.split(text, lineWidth)) {
			font.draw(this.pose, line, x, y, color);
			y += font.lineHeight;
		}
	}

	public void hLine(int minX, int maxX, int y, int color) {
		if (maxX < minX) {
			int swap = minX;
			minX = maxX;
			maxX = swap;
		}
		GuiComponent.fill(this.pose, minX, y, maxX + 1, y + 1, color);
	}

	public void fill(int minX, int minY, int maxX, int maxY, int color) {
		GuiComponent.fill(this.pose, minX, minY, maxX, maxY, color);
	}

	/** An item in the current pose (1.19.2's item renderer draws in the model-view stack only). */
	public void renderItem(ItemStack stack, int x, int y) {
		PoseStack modelView = RenderSystem.getModelViewStack();
		modelView.pushPose();
		modelView.mulPoseMatrix(this.pose.last().pose());
		RenderSystem.applyModelViewMatrix();
		Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
		modelView.popPose();
		RenderSystem.applyModelViewMatrix();
	}

	/** A tooltip over the open screen, if any. */
	public void renderTooltip(Font font, Component text, int x, int y) {
		Screen screen = Minecraft.getInstance().screen;
		if (screen != null) {
			screen.renderTooltip(this.pose, text, x, y);
		}
	}

	/** An item's tooltip (Screen.renderTooltip(ItemStack) is protected in 1.19.2). */
	public void renderTooltip(Font font, ItemStack stack, int x, int y) {
		Screen screen = tooltipScreen();
		screen.renderTooltip(this.pose, screen.getTooltipFromItem(stack), stack.getTooltipImage(), x, y);
	}

	/** A multi-line tooltip, also outside screens (the magnifying glass overlay draws one on the HUD). */
	public void renderTooltip(Font font, List<Component> lines, Optional<TooltipComponent> image, int x, int y) {
		tooltipScreen().renderTooltip(this.pose, lines, image, x, y);
	}

	public int guiWidth() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth();
	}

	public int guiHeight() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight();
	}

	private static Screen hudTooltipScreen;

	/** 1.19.2 draws tooltips through a {@link Screen}; the HUD has none, so a blank one sized to the window is used. */
	private static Screen tooltipScreen() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null) {
			return minecraft.screen;
		}
		if (hudTooltipScreen == null) {
			hudTooltipScreen = new Screen(Component.empty()) {
			};
		}
		int width = minecraft.getWindow().getGuiScaledWidth();
		int height = minecraft.getWindow().getGuiScaledHeight();
		if (hudTooltipScreen.width != width || hudTooltipScreen.height != height) {
			hudTooltipScreen.init(minecraft, width, height);
		}
		return hudTooltipScreen;
	}

}
