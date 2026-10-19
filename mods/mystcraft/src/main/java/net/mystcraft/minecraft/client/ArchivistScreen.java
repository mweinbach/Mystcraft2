package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.exploration.ArchivistMenu;
import net.mystcraft.minecraft.writing.DynamicSymbolPages;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Original 176x181 shop background, three page cards and direct booster/currency purchase controls. */
public final class ArchivistScreen extends AbstractContainerScreen<ArchivistMenu> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("mystcraft","gui/tradeshop.png");
    private final Button[] buy=new Button[4];
    public ArchivistScreen(ArchivistMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=181;inventoryLabelY=87;titleLabelY=-100;}
    @Override protected void init(){super.init();for(int i=0;i<3;i++){final int index=i;buy[i]=addRenderableWidget(new Button(leftPos+28+i*49,topPos+63,46,10,Component.translatable("gui.mystcraft.buy"),button->purchase(index)));}buy[3]=addRenderableWidget(new Button(leftPos+7,topPos+27,18,18,Component.literal("+"),button->purchase(3)));}
    private void purchase(int index){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,index);}
    @Override protected void containerTick(){super.containerTick();for(int i=0;i<4;i++)if(buy[i]!=null)buy[i].active=!menu.catchingUp()&&menu.emeralds()>=menu.price(i)&&(i==3?menu.boosters()>0:!menu.page(i).isEmpty());}
    @Override protected void renderBg(PoseStack pose,float partial,int x,int y){
        RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderTexture(0,TEXTURE);blit(pose,leftPos,topPos,0,0,imageWidth,imageHeight);
        for(int i=0;i<3;i++){int left=leftPos+28+i*49;fill(pose,left,topPos+4,left+46,topPos+43,0xff444444);fill(pose,left+1,topPos+5,left+45,topPos+42,0xff888888);fill(pose,left,topPos+43,left+46,topPos+63,0x7f000000);}
        itemRenderer.renderAndDecorateItem(new ItemStack(MystcraftItems.BOOSTER.get()),leftPos+8,topPos+9);itemRenderer.renderGuiItemDecorations(font,new ItemStack(MystcraftItems.BOOSTER.get(),Math.max(1,menu.boosters())),leftPos+8,topPos+9,Integer.toString(menu.boosters()));
        itemRenderer.renderAndDecorateItem(new ItemStack(Items.EMERALD),leftPos+122,topPos+79);
    }
    @Override protected void renderLabels(PoseStack pose,int x,int y){
        font.draw(pose,playerInventoryTitle,8,87,0x404040);
        for(int i=0;i<3;i++){int left=28+i*49;Component name=caption(menu.page(i));float scale=Math.min(1F,42F/Math.max(1,font.width(name)));pose.pushPose();pose.translate(left+2,45,0);pose.scale(scale,scale,1);font.draw(pose,name,0,0,0xdddddd);pose.popPose();font.draw(pose,Integer.toString(menu.price(i)),left+1,54,0x88ff88);}
        font.draw(pose,Integer.toString(menu.emeralds()),136,81,0x88ff88);
        font.draw(pose,"20",7,48,0x408040);
        if(menu.catchingUp())font.draw(pose,Component.translatable("gui.mystcraft.restocking"),8,77,0x404040);
    }
    private static Component caption(ItemStack page){
        if(page.isEmpty())return Component.empty();
        if(DynamicSymbolPages.isDynamic(page))return DynamicSymbolPages.displayName(page);
        return PageData.legacySymbol(page).<Component>map(id->Component.literal(SymbolRegistry.find(id).map(SymbolRegistry.Definition::displayName).orElse("Unknown: "+id))).orElseGet(page::getHoverName);
    }
    @Override public void render(PoseStack pose,int x,int y,float partial){
        renderBackground(pose);super.render(pose,x,y,partial);renderTooltip(pose,x,y);
        for(int i=0;i<3;i++){int left=leftPos+28+i*49;if(!menu.page(i).isEmpty()&&x>=left&&x<left+46&&y>=topPos+43&&y<topPos+53)renderTooltip(pose,caption(menu.page(i)),x,y);}
        if(x>=leftPos+7&&x<leftPos+25&&y>=topPos+27&&y<topPos+45)renderTooltip(pose,Component.translatable("gui.mystcraft.buy_booster",20),x,y);
    }
}
