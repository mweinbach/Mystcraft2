package net.mystcraft.minecraft.client.age;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.minecraft.instability.ClassicLightningBolt;

/** Native bolt branches/layers with source RGB tint and alpha0.51 on every vertex. */
public final class ClassicLightningRenderer extends EntityRenderer<ClassicLightningBolt> {
    private final LightningBoltRenderer nativeRenderer;
    public ClassicLightningRenderer(EntityRendererProvider.Context context){super(context);nativeRenderer=new LightningBoltRenderer(context);}
    @Override public void render(ClassicLightningBolt bolt,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        MultiBufferSource tinted=type->new Tint(buffers.getBuffer(type),bolt.red(),bolt.green(),bolt.blue());
        nativeRenderer.render(bolt,yaw,partial,pose,tinted,light);
    }
    @Override public ResourceLocation getTextureLocation(ClassicLightningBolt bolt){return null;}
    private record Tint(VertexConsumer delegate,float red,float green,float blue) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x,double y,double z){delegate.vertex(x,y,z);return this;}
        @Override public VertexConsumer color(int r,int g,int b,int a){delegate.color(red,green,blue,.51F);return this;}
        @Override public VertexConsumer uv(float u,float v){delegate.uv(u,v);return this;}
        @Override public VertexConsumer overlayCoords(int u,int v){delegate.overlayCoords(u,v);return this;}
        @Override public VertexConsumer uv2(int u,int v){delegate.uv2(u,v);return this;}
        @Override public VertexConsumer normal(float x,float y,float z){delegate.normal(x,y,z);return this;}
        @Override public void endVertex(){delegate.endVertex();}
        @Override public void defaultColor(int r,int g,int b,int a){delegate.defaultColor((int)(red*255),(int)(green*255),(int)(blue*255),130);}
        @Override public void unsetDefaultColor(){delegate.unsetDefaultColor();}
    }
}
