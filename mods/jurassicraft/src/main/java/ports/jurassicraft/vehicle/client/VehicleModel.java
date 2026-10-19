package ports.jurassicraft.vehicle.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

/** Static Tabula cuboids copied from the original model. */
public final class VehicleModel {
    private final List<ModelPart> roots = new ArrayList<>();
    private final Map<String, NamedPart> named = new LinkedHashMap<>();
    private record NamedPart(ModelPart part, float x, float y, float z, float baseY) {}
    private final float[] scale;
    public VehicleModel(String name) {
        ResourceLocation path = new ResourceLocation(PortMod.MOD_ID, "models/entity_port/" + name + ".json");
        try (var stream = Minecraft.getInstance().getResourceManager().getResource(path).orElseThrow().open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            int width = data.get("textureWidth").getAsInt(), height = data.get("textureHeight").getAsInt();
            scale = array(data.getAsJsonArray("scale"));
            for (var cube : data.getAsJsonArray("cubes")) roots.add(part(cube.getAsJsonObject(), width, height));
        } catch (Exception e) { throw new IllegalStateException("Cannot load vehicle model " + path, e); }
    }
    private ModelPart part(JsonObject cube, int texWidth, int texHeight) {
        float[] dimensions = array(cube.getAsJsonArray("dimensions")), offset = array(cube.getAsJsonArray("offset"));
        float[] uv = array(cube.getAsJsonArray("txOffset")), position = array(cube.getAsJsonArray("position"));
        float[] rotation = array(cube.getAsJsonArray("rotation")), scale = array(cube.getAsJsonArray("scale"));
        float inflate = cube.has("mcScale") ? cube.get("mcScale").getAsFloat() : 0;
        boolean mirror = cube.has("txMirror") && cube.get("txMirror").getAsBoolean();
        List<ModelPart.Cube> boxes = List.of(new ModelPart.Cube((int)uv[0], (int)uv[1],
                offset[0], offset[1], offset[2], dimensions[0], dimensions[1], dimensions[2],
                inflate, inflate, inflate, mirror, texWidth, texHeight));
        Map<String, ModelPart> children = new LinkedHashMap<>();
        int i = 0;
        for (var child : cube.getAsJsonArray("children")) children.put(Integer.toString(i++), part(child.getAsJsonObject(), texWidth, texHeight));
        ModelPart part = new ModelPart(boxes, children);
        part.setPos(position[0], position[1], position[2]);
        part.setRotation((float)Math.toRadians(rotation[0]), (float)Math.toRadians(rotation[1]), (float)Math.toRadians(rotation[2]));
        part.xScale = scale[0]; part.yScale = scale[1]; part.zScale = scale[2];
        part.skipDraw = cube.has("hidden") && cube.get("hidden").getAsBoolean();
        if (cube.has("name")) named.putIfAbsent(cube.get("name").getAsString(), new NamedPart(part, part.xRot, part.yRot, part.zRot, part.y));
        return part;
    }
    private static float[] array(JsonArray values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i).getAsFloat();
        return result;
    }
    public void resetAnimations() {
        for (NamedPart p : named.values()) {
            p.part().setRotation(p.x(), p.y(), p.z());
            p.part().y = p.baseY();
        }
    }
    public void rotateX(String name, float radians) { NamedPart p = named.get(name); if (p != null) p.part().xRot = radians; }
    public void rotateY(String name, float radians) { NamedPart p = named.get(name); if (p != null) p.part().yRot = radians; }
    public void rotateZ(String name, float radians) { NamedPart p = named.get(name); if (p != null) p.part().zRot = radians; }
    public void offsetY(String name, float blocks) { NamedPart p = named.get(name); if (p != null) p.part().y = p.baseY() + blocks * 16; }
    public void render(PoseStack pose, VertexConsumer buffer, int light) { render(pose, buffer, light, 1, 1, 1, 1); }
    public void render(PoseStack pose, VertexConsumer buffer, int light, float red, float green, float blue, float alpha) {
        pose.pushPose();
        pose.scale(scale[0], scale[1], scale[2]);
        for (ModelPart root : roots) root.render(pose, buffer, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, red, green, blue, alpha);
        pose.popPose();
    }
}
