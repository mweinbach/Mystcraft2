package ports.jurassicraft.skull.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;

/** Renders the original nested Tabula cubes, including the no-stand pose. */
final class SkullModel {
    private final List<ModelPart> roots=new ArrayList<>();
    private final Map<String,ModelPart> parts=new LinkedHashMap<>();
    private final float[] scale;
    SkullModel(boolean wall,boolean stand) {
        ResourceLocation path=new ResourceLocation(PortMod.MOD_ID,"models/skull_port/tyrannosaurus_"+(wall ? "vertical" : "horizontal")+".json");
        try (var stream=Minecraft.getInstance().getResourceManager().getResource(path).orElseThrow().open();
             var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            JsonObject data=JsonParser.parseReader(reader).getAsJsonObject();
            int width=data.get("textureWidth").getAsInt(),height=data.get("textureHeight").getAsInt();
            scale=array(data.getAsJsonArray("scale"));
            for (var cube : data.getAsJsonArray("cubes")) roots.add(part(cube.getAsJsonObject(),width,height));
            if (!wall && !stand) {
                if (parts.containsKey("Standard part1")) parts.get("Standard part1").visible=false;
                ModelPart head=parts.get("head"),jaw=parts.get("jawLower1");
                if (head!=null) { head.y=14.1F; head.xRot=-.07F; }
                if (jaw!=null) jaw.xRot=.155F;
            }
        } catch (IOException | RuntimeException e) { throw new IllegalStateException("Could not load original skull display model "+path,e); }
    }
    private ModelPart part(JsonObject cube,int texWidth,int texHeight) {
        float[] d=array(cube.getAsJsonArray("dimensions")),o=array(cube.getAsJsonArray("offset")),uv=array(cube.getAsJsonArray("txOffset"));
        float[] p=array(cube.getAsJsonArray("position")),r=array(cube.getAsJsonArray("rotation")),s=array(cube.getAsJsonArray("scale"));
        float inflation=cube.has("mcScale") ? cube.get("mcScale").getAsFloat() : 0;
        boolean mirror=cube.has("txMirror") && cube.get("txMirror").getAsBoolean();
        var boxes=List.of(new ModelPart.Cube((int)uv[0],(int)uv[1],o[0],o[1],o[2],d[0],d[1],d[2],inflation,inflation,inflation,mirror,texWidth,texHeight));
        Map<String,ModelPart> children=new LinkedHashMap<>(); int index=0;
        for (var child : cube.getAsJsonArray("children")) children.put(Integer.toString(index++),part(child.getAsJsonObject(),texWidth,texHeight));
        ModelPart result=new ModelPart(boxes,children); result.setPos(p[0],p[1],p[2]);
        result.setRotation((float)Math.toRadians(r[0]),(float)Math.toRadians(r[1]),(float)Math.toRadians(r[2]));
        result.xScale=s[0]; result.yScale=s[1]; result.zScale=s[2];
        result.skipDraw=cube.has("hidden") && cube.get("hidden").getAsBoolean();
        parts.put(cube.get("name").getAsString(),result);
        return result;
    }
    private static float[] array(JsonArray input) { float[] values=new float[input.size()]; for (int i=0;i<values.length;i++) values[i]=input.get(i).getAsFloat(); return values; }
    void render(PoseStack pose,VertexConsumer buffer,int light,int overlay) {
        pose.pushPose(); pose.scale(scale[0],scale[1],scale[2]);
        for (ModelPart part : roots) part.render(pose,buffer,light,overlay);
        pose.popPose();
    }
}
