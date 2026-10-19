package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** GuiUtils.drawPage: original30x40 page fragment and four-word atlas geometry. */
final class ClassicPageSurface extends GuiComponent {
    private static final ResourceLocation PAGE = new ResourceLocation("mystcraft", "gui/bookui_pagel.png");
    static final ResourceLocation DESK = new ResourceLocation("mystcraft", "gui/writingdesk.png");
    private ClassicPageSurface() {}
    static void draw(PoseStack pose, PageBrowserIndex.Entry entry, int x, int y, int width, int height) {
        if (DynamicPageRenderer.draw(pose,entry.page(),x,y,width,height,entry.count()==0?.35F:1)) return;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, PAGE);
        RenderSystem.setShaderColor(entry.count() == 0 ? .35F : 1, entry.count() == 0 ? .35F : 1, entry.count() == 0 ? .35F : 1, 1);
        pose.pushPose(); pose.translate(x, y, 0); pose.scale(width / 30F, height / 40F, 1);
        blit(pose, 0, 0, 156, 0, 30, 40, 256, 256);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (PageData.isLinkPanel(entry.page())) fill(pose, 5, 6, 25, 20, 0xff000000);
        else if (PageData.legacySymbol(entry.page()).isPresent())
            ClassicBookGlyphs.draw(pose, PageData.symbol(entry.page()).flatMap(SymbolRegistry::find).orElse(null), .5F, 5.5F, 29);
        pose.popPose();
    }
}
