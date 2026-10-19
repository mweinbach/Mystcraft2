package ports.jurassicraft.cultivator;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.PortMod;

public final class CultivatorScreen extends AbstractContainerScreen<CultivatorMenu> {
    private static final ResourceLocation GUI=new ResourceLocation(PortMod.MOD_ID,"textures/gui/cultivator.png");
    private static final ResourceLocation NUTRIENTS=new ResourceLocation(PortMod.MOD_ID,"textures/gui/cultivator_nutrients.png");
    public CultivatorScreen(CultivatorMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title); imageWidth=352; imageHeight=188;
        titleLabelX=(176-font.width(title))/2; titleLabelY=10; inventoryLabelY=imageHeight-96+2;
    }
    @Override protected void renderBg(PoseStack pose,float partialTick,int mouseX,int mouseY) {
        RenderSystem.setShaderColor(1,1,1,1); RenderSystem.setShaderTexture(0,GUI);
        blit(pose,leftPos,topPos,0,0,176,188);
        blit(pose,leftPos+48,topPos+18,0,188,42,67-menu.field(2)*67/2);
        blit(pose,leftPos+59+menu.field(7)*20/100,topPos+88,176,0,3,5);
        RenderSystem.setShaderTexture(0,NUTRIENTS); blit(pose,leftPos+176,topPos,0,0,176,166);
        for (int i=0;i<4;i++) blit(pose,leftPos+190,topPos+56+i*26,0,166+i*9,menu.field(3+i)*150/CultivatorBlockEntity.MAX_NUTRIENTS,9);
    }
    @Override protected void renderLabels(PoseStack pose,int mouseX,int mouseY) {
        super.renderLabels(pose,mouseX,mouseY);
        for (int i=0;i<4;i++) font.draw(pose,Component.translatable("gui.jurassicraft.cultivator_nutrient_"+i),200,48+i*26,4210752);
        int total=menu.field(1); if (total>0) font.draw(pose,Component.translatable("gui.jurassicraft.cultivator_progress",menu.field(0)*100/total),97,68,4210752);
    }
    private boolean changeTemperature(double x,double y) {
        int sx=leftPos+59, sy=topPos+88;
        if (x>sx && x<sx+21 && y>sy && y<sy+5 && minecraft!=null && minecraft.gameMode!=null) {
            int step=Math.max(0,Math.min(20,(int)(x-sx))); minecraft.gameMode.handleInventoryButtonClick(menu.containerId,step); return true;
        }
        return false;
    }
    @Override public boolean mouseClicked(double x,double y,int button) { return changeTemperature(x,y) || super.mouseClicked(x,y,button); }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { return changeTemperature(x,y) || super.mouseDragged(x,y,button,dx,dy); }
    @Override public void render(PoseStack pose,int x,int y,float partial) {
        renderBackground(pose); super.render(pose,x,y,partial); renderTooltip(pose,x,y);
        if (x>leftPos+59 && x<leftPos+80 && y>topPos+88 && y<topPos+93) {
            int t=menu.field(7); renderTooltip(pose,Component.translatable("tooltip.jurassicraft.incubator_temperature",t,
                    Component.translatable(t>50 ? "tooltip.jurassicraft.incubator_male" : "tooltip.jurassicraft.incubator_female")),x,y);
        }
    }
}
