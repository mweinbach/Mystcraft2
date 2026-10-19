package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Original slider atlas and GuiElementButtonBase bevel/toggle semantics. */
final class ClassicPageWidgets extends GuiComponent {
    private static final ResourceLocation SCROLL = new ResourceLocation("mystcraft", "gui/scrollbar.png");
    private static final ResourceLocation SLOTS = new ResourceLocation("minecraft", "textures/gui/container/stats_icons.png");
    static void slider(PoseStack pose, ClassicPageScroll scroll, int x, int y, int height) {
        RenderSystem.setShaderTexture(0, SCROLL); RenderSystem.setShaderColor(1,1,1,1);
        blit(pose,x,y,0F,0F,20,4,32,32);
        for(int part=4;part<height-4;part+=22) blit(pose,x,y+part,0F,4F,20,Math.min(22,height-part-4),32,32);
        blit(pose,x,y+height-4,0F,26F,20,4,32,32);
        int offset = scroll.maximum()==0?0:(int)((height-23)*(scroll.pixels()/(float)scroll.maximum()));
        blit(pose,x+4,y+4+offset,20F,scroll.dragging()?15F:0F,12,15,32,32);
    }
    static void clip(int x,int y,int width,int height) {
        var window=Minecraft.getInstance().getWindow(); double scale=window.getGuiScale();
        RenderSystem.enableScissor((int)Math.floor(x*scale),(int)Math.floor(window.getHeight()-(y+height)*scale),(int)Math.ceil(width*scale),(int)Math.ceil(height*scale));
    }
    static final class SourceButton extends Button {
        private final BooleanSupplier selected;
        private boolean pressed;
        SourceButton(int x,int y,int width,int height,Component text,OnPress action,BooleanSupplier selected) {
            super(x,y,width,height,text,action); this.selected=selected;
        }
        @Override public void onClick(double mouseX,double mouseY) { pressed=true; }
        @Override public void onRelease(double mouseX,double mouseY) {
            if(pressed && active && mouseX>=x && mouseX<x+width && mouseY>=y && mouseY<y+height) onPress();
            pressed=false;
        }
        @Override public void renderButton(PoseStack pose,int mouseX,int mouseY,float partial) {
            boolean down=pressed||selected.getAsBoolean();
            if(width==height) {
                RenderSystem.setShaderTexture(0,SLOTS); RenderSystem.setShaderColor(1,1,1,1);
                blit(pose,x,y,width,height,0F,down?0F:18F,18,18,128,128);
            } else {
                int light=down?0xff373737:0xffffffff,dark=down?0xffffffff:0xff373737;
                fill(pose,x,y,x+width,y+height,0xff8b8b8b);fill(pose,x,y,x+width-1,y+height-1,light);
                fill(pose,x+1,y+1,x+width,y+height,dark);fill(pose,x+1,y+1,x+width-1,y+height-1,0xff8b8b8b);
            }
            if(isHoveredOrFocused()) fill(pose,x,y,x+width,y+height,0x90ffffff);
            var font=Minecraft.getInstance().font;
            float scale=Math.min(1,(width-4F)/Math.max(1,font.width(getMessage())));
            pose.pushPose();pose.translate(x+2,y+2,0);pose.scale(scale,scale,1);font.draw(pose,getMessage(),0,0,active?0xff000000:0xff333333);pose.popPose();
        }
    }
}
