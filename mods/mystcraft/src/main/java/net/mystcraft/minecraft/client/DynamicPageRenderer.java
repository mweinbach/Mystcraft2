package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.registry.AgeRegistry;
import net.mystcraft.minecraft.writing.DynamicSymbolPages;

/** Render-thread-owned LRU textures/meshes for bounded dynamic biome/fluid page poems. */
public final class DynamicPageRenderer {
    private static final int LIMIT=128;
    private static final ResourceLocation BACKGROUND=new ResourceLocation("mystcraft","textures/item/page_background.png");
    private static final ResourceLocation ATLAS=new ResourceLocation("mystcraft","textures/symbolcomponents.png");
    private static final Map<List<String>,Page> CACHE=new LinkedHashMap<>(16,.75F,true);
    private static BufferedImage background,atlas;
    private static boolean reloadRegistered;
    private record Face(float x0,float y0,float z0,float x1,float y1,float z1,float x2,float y2,float z2,float x3,float y3,float z3,
                        float u0,float v0,float u1,float v1,float nx,float ny,float nz) {}
    private record Page(ResourceLocation id,DynamicTexture texture,List<Face> faces) {}
    private DynamicPageRenderer() {}
    private static void initialize() {
        if(reloadRegistered)return;
        if(Minecraft.getInstance().getResourceManager() instanceof ReloadableResourceManager manager) {
            manager.registerReloadListener((ResourceManagerReloadListener)ignored->clear());reloadRegistered=true;
        }
    }
    public static void clear() {
        if(!RenderSystem.isOnRenderThread()){RenderSystem.recordRenderCall(DynamicPageRenderer::clear);return;}
        for(Page page:CACHE.values())if(page!=null)dispose(page);
        CACHE.clear();background=null;atlas=null;
    }
    private static void dispose(Page page){Minecraft.getInstance().getTextureManager().release(page.id());page.texture().close();}
    private static boolean dynamic(ItemStack stack){return stack.is(AgeRegistry.PAGE.get())&&DynamicSymbolPages.isDynamic(stack);}
    private static Page page(ItemStack stack) {
        RenderSystem.assertOnRenderThread();initialize();
        var raw=DynamicSymbolPages.poem(stack);
        if(raw.size()!=4 || raw.stream().anyMatch(word->word==null||word.length()>512))return null;
        List<String> poem=List.copyOf(raw);
        if(CACHE.containsKey(poem))return CACHE.get(poem);
        Page result=null;
        try {
            if(background==null)background=read(BACKGROUND);if(atlas==null)atlas=read(ATLAS);
            BufferedImage image=DynamicPageImages.compose(poem,background,atlas);
            NativeImage pixels=new NativeImage(128,128,false);
            for(int x=0;x<128;x++)for(int y=0;y<128;y++){int argb=image.getRGB(x,y);pixels.setPixelRGBA(x,y,(argb&0xff00ff00)|((argb>>>16)&255)|((argb&255)<<16));}
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\u0000",poem).getBytes(StandardCharsets.UTF_8)));
            ResourceLocation id=new ResourceLocation("mystcraft","dynamic_page/"+hash);
            DynamicTexture texture=new DynamicTexture(pixels);
            Minecraft.getInstance().getTextureManager().register(id,texture);
            result=new Page(id,texture,mesh(image));
        }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Cannot compose dynamic page poem {}",poem,failure);}
        CACHE.put(poem,result);
        while(CACHE.size()>LIMIT){var oldest=CACHE.entrySet().iterator();Page removed=oldest.next().getValue();oldest.remove();if(removed!=null)dispose(removed);}
        return result;
    }
    private static BufferedImage read(ResourceLocation location)throws IOException {
        try(var stream=Minecraft.getInstance().getResourceManager().open(location);NativeImage image=NativeImage.read(stream)) {
            if(image.getWidth()>4096||image.getHeight()>4096)throw new IOException("Dynamic page source exceeds4096pixels");
            BufferedImage converted=new BufferedImage(image.getWidth(),image.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int x=0;x<image.getWidth();x++)for(int y=0;y<image.getHeight();y++){int rgba=image.getPixelRGBA(x,y);converted.setRGB(x,y,(rgba&0xff00ff00)|((rgba>>>16)&255)|((rgba&255)<<16));}
            return converted;
        }
    }
    @FunctionalInterface public interface Transform { void apply(BakedModel model,ItemTransforms.TransformType type,boolean leftHand,PoseStack pose); }
    /** Same model transform/depth as generated paper; side faces follow alpha silhouette. */
    public static boolean render(ItemStack stack,ItemTransforms.TransformType type,boolean leftHand,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model) {
        return render(stack,type,leftHand,pose,buffers,light,overlay,model,(m,t,left,p)->m.getTransforms().getTransform(t).apply(left,p));
    }
    public static boolean render(ItemStack stack,ItemTransforms.TransformType type,boolean leftHand,PoseStack pose,MultiBufferSource buffers,int light,int overlay,BakedModel model,Transform transform) {
        if(!dynamic(stack))return false;Page page=page(stack);if(page==null)return false;
        pose.pushPose();
        try {
            transform.apply(model,type,leftHand,pose);pose.translate(-.5,-.5,-.5);
            VertexConsumer output=buffers.getBuffer(RenderType.entityCutoutNoCull(page.id()));
            var frame=pose.last();
            for(Face f:page.faces()) {
                vertex(output,frame,f.x0,f.y0,f.z0,f.u0,f.v1,f,light,overlay);
                vertex(output,frame,f.x1,f.y1,f.z1,f.u1,f.v1,f,light,overlay);
                vertex(output,frame,f.x2,f.y2,f.z2,f.u1,f.v0,f,light,overlay);
                vertex(output,frame,f.x3,f.y3,f.z3,f.u0,f.v0,f,light,overlay);
            }
        }finally{pose.popPose();}
        return true;
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,float x,float y,float z,float u,float v,Face f,int light,int overlay) {
        out.vertex(pose.pose(),x,y,z).color(255,255,255,255).uv(u,v).overlayCoords(overlay).uv2(light).normal(pose.normal(),f.nx,f.ny,f.nz).endVertex();
    }
    /** Direct book/workspace presentation; returns false for static/unknown non-dynamic pages. */
    public static boolean draw(PoseStack pose,ItemStack stack,int x,int y,int width,int height) {
        return draw(pose,stack,x,y,width,height,1);
    }
    public static boolean draw(PoseStack pose,ItemStack stack,int x,int y,int width,int height,float brightness) {
        if(!dynamic(stack))return false;Page page=page(stack);if(page==null)return false;
        RenderSystem.setShader(GameRenderer::getPositionTexShader);RenderSystem.setShaderTexture(0,page.id());RenderSystem.setShaderColor(brightness,brightness,brightness,1);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        GuiComponent.blit(pose,x,y,width,height,0,0,128,128,128,128);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();return true;
    }
    private static boolean opaque(BufferedImage image,int x,int y){return x>=0&&x<128&&y>=0&&y<128&&(image.getRGB(x,y)>>>24)!=0;}
    private static void span(int[][] minimum,int[][] maximum,int side,int anchor,int variable){minimum[side][anchor]=Math.min(minimum[side][anchor],variable);maximum[side][anchor]=Math.max(maximum[side][anchor],variable);}
    private static List<Face> mesh(BufferedImage image) {
        var faces=new ArrayList<Face>();float front=8.5F/16,back=7.5F/16;
        faces.add(new Face(0,0,front,1,0,front,1,1,front,0,1,front,0,0,1,1,0,0,1));
        faces.add(new Face(1,0,back,0,0,back,0,1,back,1,1,back,1,0,0,1,0,0,-1));
        // ItemModelGenerator merges each facing's silhouette spans by their fixed anchor.
        // This bounds even a checkerboard resource pack to512edge quads, rather than65536.
        int[][] minimum=new int[4][128],maximum=new int[4][128];
        for(int side=0;side<4;side++){java.util.Arrays.fill(minimum[side],128);java.util.Arrays.fill(maximum[side],-1);}
        for(int x=0;x<128;x++)for(int y=0;y<128;y++)if(opaque(image,x,y)) {
            if(!opaque(image,x,y-1))span(minimum,maximum,0,y,x);
            if(!opaque(image,x,y+1))span(minimum,maximum,1,y,x);
            if(!opaque(image,x-1,y))span(minimum,maximum,2,x,y);
            if(!opaque(image,x+1,y))span(minimum,maximum,3,x,y);
        }
        for(int anchor=0;anchor<128;anchor++)for(int side=0;side<4;side++)if(maximum[side][anchor]>=0) {
            float min=minimum[side][anchor]/128F,max=(maximum[side][anchor]+1)/128F;
            float edge=anchor/128F,center=(anchor+.5F)/128;
            if(side==0)faces.add(new Face(min,1-edge,front,max,1-edge,front,max,1-edge,back,min,1-edge,back,min,center,max,center,0,1,0));
            if(side==1)faces.add(new Face(min,1-edge-1/128F,back,max,1-edge-1/128F,back,max,1-edge-1/128F,front,min,1-edge-1/128F,front,min,center,max,center,0,-1,0));
            if(side==2)faces.add(new Face(edge,1-max,back,edge,1-max,front,edge,1-min,front,edge,1-min,back,center,min,center,max,-1,0,0));
            if(side==3)faces.add(new Face(edge+1/128F,1-max,front,edge+1/128F,1-max,back,edge+1/128F,1-min,back,edge+1/128F,1-min,front,center,min,center,max,1,0,0));
        }
        return List.copyOf(faces);
    }
}
