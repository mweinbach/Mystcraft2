package ports.jurassicraft.bugs;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import ports.jurassicraft.PortMod;

public final class BugCrateScreen extends AbstractContainerScreen<BugCrateMenu> {
    private static final ResourceLocation GUI=new ResourceLocation(PortMod.MOD_ID,"textures/gui/bug_crate.png");
    public BugCrateScreen(BugCrateMenu menu,Inventory inventory,Component title) { super(menu,inventory,title); titleLabelX=(176-font.width(title))/2; }
    @Override protected void renderBg(PoseStack pose,float partial,int mouseX,int mouseY) {
        RenderSystem.setShaderColor(1,1,1,1); RenderSystem.setShaderTexture(0,GUI);
        blit(pose,leftPos,topPos,0,0,imageWidth,imageHeight);
        blit(pose,leftPos+88,topPos+35,176,0,menu.progress(24),17);
    }
    @Override public void render(PoseStack pose,int x,int y,float partial) { renderBackground(pose); super.render(pose,x,y,partial); renderTooltip(pose,x,y); }
}
