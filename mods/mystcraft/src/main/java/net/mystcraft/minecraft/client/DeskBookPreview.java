package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.writing.PageData;

/** The source desk embeds the same book artwork at90x50, with independent browsing. */
final class DeskBookPreview extends GuiComponent {
    private static ResourceLocation texture(String file) { return new ResourceLocation("mystcraft", "gui/" + file); }
    static List<CompoundTag> pages(ItemStack book) {
        var pages = book.hasTag() ? PageData.readPages(book.getTag()) : List.<CompoundTag>of();
        if (!pages.isEmpty()) return pages;
        CompoundTag panel = new CompoundTag(); PageData.makeLinkPanel(panel); return List.of(panel);
    }
    private static void stamp(PoseStack pose,int x,int y,int u,int v,int w,int h) { GuiComponent.blit(pose,x,y,0,(float)u,(float)v,w,h,256,256); }
    static void draw(PoseStack pose, Font font, ItemStack book, int index, int x, int y) {
        pose.pushPose(); pose.translate(x,y,0); pose.scale(90F/327F,50F/199F,1);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1,1,1,1);
        RenderSystem.setShaderTexture(0,texture("bookui_cover.png"));
        stamp(pose,0,7,152,0,34,192); stamp(pose,34,7,49,0,103,192); stamp(pose,137,7,45,0,4,192); stamp(pose,141,7,0,0,186,192);
        if(AgeBookData.isDescriptive(book)) { stamp(pose,0,7,186,0,34,192);stamp(pose,293,7,186,0,34,192); }
        var pages=pages(book);
        if(index>0) { RenderSystem.setShaderTexture(0,texture("bookui_pagel.png"));stamp(pose,7,0,0,0,156,195); }
        if(index==0) {
            fill(pose,173,20,305,103,AgeBookData.isUnallocated(book)?0xff000000:0xff000044);
            RenderSystem.setShaderTexture(0,texture("bookui_pager.png"));stamp(pose,163,0,0,0,156,195);
            int lineY=40;for(var line:font.split(book.getHoverName(),102)) { if(lineY>67)break;font.draw(pose,line,40,lineY,0);lineY+=font.lineHeight; }
        } else if(index<pages.size()) {
            RenderSystem.setShaderTexture(0,texture("bookui_rpage_full.png"));stamp(pose,163,0,0,0,156,195);
            var page=PageBrowserIndex.page(pages.get(index));
            if(!DynamicPageRenderer.draw(pose,page,171,25,140,140) && PageData.legacySymbol(pages.get(index)).isPresent())
                ClassicBookGlyphs.draw(pose,PageData.legacySymbol(pages.get(index)).flatMap(net.mystcraft.minecraft.writing.SymbolRegistry::find).orElse(null),171,25,140);
        }
        font.draw(pose,index+"/"+pages.size(),150,185,0);
        RenderSystem.disableBlend();pose.popPose();
    }
}
