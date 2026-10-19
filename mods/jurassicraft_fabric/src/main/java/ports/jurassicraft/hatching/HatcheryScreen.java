package ports.jurassicraft.hatching;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.PortMod;

public final class HatcheryScreen extends AbstractContainerScreen<HatcheryMenu> {
    private static final int[] SLOT_X={33,56,79,102,125}, SLOT_Y={28,21,14,21,28};
    private final ResourceLocation texture;
    public HatcheryScreen(HatcheryMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title);
        texture=new ResourceLocation(PortMod.MOD_ID,"textures/gui/"+menu.machine().id+".png");
        titleLabelX=(imageWidth-font.width(title))/2;
    }
    @Override protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1,1,1,1); RenderSystem.setShaderTexture(0,texture);
        blit(pose,leftPos,topPos,0,0,imageWidth,imageHeight);
        switch (menu.machine()) {
            case EMBRYONIC -> blit(pose,leftPos+79,topPos+34,176,14,menu.progressScaled(0,24)+1,16);
            case CALCIFIER -> blit(pose,leftPos+67,topPos+31,176,14,menu.progressScaled(0,24)+1,16);
            case INCUBATOR -> { for (int i=0;i<5;i++) {
                blit(pose,leftPos+SLOT_X[i]+1,topPos+SLOT_Y[i]+24,176,5,menu.progressScaled(i,14),5);
                blit(pose,leftPos+SLOT_X[i]+1+menu.temperature(i)*20/100-3,topPos+SLOT_Y[i]+18,176,0,3,5);
            }}
        }
    }
    private boolean changeTemperature(double mouseX, double mouseY) {
        if (menu.machine()!=HatcheryRegistry.Machine.INCUBATOR || minecraft == null || minecraft.gameMode == null) return false;
        for (int i=0;i<5;i++) {
            int x=leftPos+SLOT_X[i]-2, y=topPos+SLOT_Y[i]+18;
            if (mouseX>x && mouseX<x+21 && mouseY>y && mouseY<y+5) {
                int step=Math.max(0,Math.min(20,(int)(mouseX-x)));
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,i*HatcheryMenu.TEMPERATURE_STEPS+step);
                return true;
            }
        }
        return false;
    }
    @Override public boolean mouseClicked(double x,double y,int button) { return changeTemperature(x,y) || super.mouseClicked(x,y,button); }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { return changeTemperature(x,y) || super.mouseDragged(x,y,button,dx,dy); }
    @Override public void render(PoseStack pose,int x,int y,float partialTick) {
        renderBackground(pose); super.render(pose,x,y,partialTick); renderTooltip(pose,x,y);
        if (menu.machine() == HatcheryRegistry.Machine.INCUBATOR) for (int i = 0; i < 5; i++) {
            int sx = leftPos + SLOT_X[i] - 2, sy = topPos + SLOT_Y[i] + 18;
            if (x > sx && x < sx + 21 && y > sy && y < sy + 5) {
                int temperature = menu.temperature(i);
                renderTooltip(pose, Component.translatable("tooltip.jurassicraft.incubator_temperature", temperature,
                        Component.translatable(temperature > 50 ? "tooltip.jurassicraft.incubator_male" : "tooltip.jurassicraft.incubator_female")), x, y);
                break;
            }
        }
    }
}
