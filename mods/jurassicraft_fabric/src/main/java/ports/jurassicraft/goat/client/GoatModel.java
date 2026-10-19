package ports.jurassicraft.goat.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ports.jurassicraft.PortMod;

import ports.jurassicraft.goat.JurassicGoat;

/** The original goat Tabula geometry and pose keyframes. */
public final class GoatModel extends EntityModel<JurassicGoat> {
    private final List<ModelPart> roots = new ArrayList<>();
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final Map<String, List<Frame>> animations = new HashMap<>();
    private final float[] modelScale;

    public GoatModel(String kind) {
        ResourceLocation path = new ResourceLocation(PortMod.MOD_ID, "models/entity_port/goat_" + kind + ".json");
        try (var stream = Minecraft.getInstance().getResourceManager().getResource(path).orElseThrow().open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            int width = data.get("textureWidth").getAsInt(), height = data.get("textureHeight").getAsInt();
            modelScale = array(data.getAsJsonArray("scale"));
            for (var c : data.getAsJsonArray("cubes")) roots.add(part(c.getAsJsonObject(), width, height));
            for (var anim : data.getAsJsonObject("animations").entrySet()) {
                List<Frame> frames = new ArrayList<>();
                for (var raw : anim.getValue().getAsJsonArray()) {
                    JsonObject value = raw.getAsJsonObject();
                    Map<String, Transform> transforms = new HashMap<>();
                    for (var entry : value.getAsJsonObject("parts").entrySet()) {
                        JsonArray vectors = entry.getValue().getAsJsonArray();
                        transforms.put(entry.getKey(), new Transform(array(vectors.get(0).getAsJsonArray()),
                                array(vectors.get(1).getAsJsonArray()), array(vectors.get(2).getAsJsonArray())));
                    }
                    frames.add(new Frame(value.get("time").getAsFloat(), transforms));
                }
                animations.put(anim.getKey(), frames);
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not load original goat model " + path, e);
        }
    }
    private ModelPart part(JsonObject cube, int texWidth, int texHeight) {
        float[] dimensions = array(cube.getAsJsonArray("dimensions"));
        float[] offset = array(cube.getAsJsonArray("offset"));
        float[] uv = array(cube.getAsJsonArray("txOffset"));
        float[] position = array(cube.getAsJsonArray("position"));
        float[] rotation = array(cube.getAsJsonArray("rotation"));
        float[] scale = array(cube.getAsJsonArray("scale"));
        float inflation = cube.has("mcScale") ? cube.get("mcScale").getAsFloat() : 0;
        boolean mirror = cube.has("txMirror") && cube.get("txMirror").getAsBoolean();
        List<ModelPart.Cube> boxes = List.of(new ModelPart.Cube((int)uv[0], (int)uv[1],
                offset[0], offset[1], offset[2], dimensions[0], dimensions[1], dimensions[2],
                inflation, inflation, inflation, mirror, texWidth, texHeight));
        Map<String, ModelPart> children = new LinkedHashMap<>();
        int childIndex = 0;
        for (var c : cube.getAsJsonArray("children")) {
            JsonObject child = c.getAsJsonObject();
            // Some upstream skeletons reuse identifiers even for siblings.
            children.put(Integer.toString(childIndex++), part(child, texWidth, texHeight));
        }
        ModelPart part = new ModelPart(boxes, children);
        part.setPos(position[0], position[1], position[2]);
        part.setRotation((float)Math.toRadians(rotation[0]), (float)Math.toRadians(rotation[1]), (float)Math.toRadians(rotation[2]));
        part.xScale = scale[0]; part.yScale = scale[1]; part.zScale = scale[2];
        part.skipDraw = cube.has("hidden") && cube.get("hidden").getAsBoolean();
        parts.put(cube.get("identifier").getAsString(), part);
        return part;
    }
    private static float[] array(JsonArray input) {
        float[] result = new float[input.size()];
        for (int i = 0; i < result.length; i++) result[i] = input.get(i).getAsFloat();
        return result;
    }
    @Override public void setupAnim(JurassicGoat entity, float limbSwing, float amount, float age, float yaw, float pitch) {
        List<Frame> idle = animations.get("IDLE");
        Map<String, Transform> defaults = idle.get(0).parts();
        String state = entity.eatingTicks() > 0 ? "EATING" : amount > .55f ? "RUNNING" : amount > .01f ? "WALKING" : "IDLE";
        List<Frame> frames = animations.getOrDefault(state, idle);
        float time = entity.eatingTicks() > 0 ? 40 - entity.eatingTicks() : age;
        float total = 0;
        for (Frame f : frames) total += f.time();
        float point = total > 0 ? time % total : 0;
        int index = 0;
        while (index < frames.size() - 1 && point >= frames.get(index).time()) point -= frames.get(index++).time();
        Frame from = frames.get(index), to = frames.get((index + 1) % frames.size());
        float alpha = from.time() > 0 ? point / from.time() : 0;
        for (var entry : parts.entrySet()) {
            Transform base = defaults.get(entry.getKey());
            if (base == null) continue;
            Transform a = from.parts().getOrDefault(entry.getKey(), base), b = to.parts().getOrDefault(entry.getKey(), base);
            ModelPart p = entry.getValue();
            p.setPos(Mth.lerp(alpha, a.position()[0], b.position()[0]), Mth.lerp(alpha, a.position()[1], b.position()[1]), Mth.lerp(alpha, a.position()[2], b.position()[2]));
            p.setRotation((float)Math.toRadians(Mth.rotLerp(alpha, a.rotation()[0], b.rotation()[0])),
                    (float)Math.toRadians(Mth.rotLerp(alpha, a.rotation()[1], b.rotation()[1])),
                    (float)Math.toRadians(Mth.rotLerp(alpha, a.rotation()[2], b.rotation()[2])));
            p.xScale = Mth.lerp(alpha, a.scale()[0], b.scale()[0]);
            p.yScale = Mth.lerp(alpha, a.scale()[1], b.scale()[1]);
            p.zScale = Mth.lerp(alpha, a.scale()[2], b.scale()[2]);
        }
    }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        pose.pushPose();
        pose.scale(modelScale[0], modelScale[1], modelScale[2]);
        for (ModelPart root : roots) root.render(pose, buffer, light, overlay, red, green, blue, alpha);
        pose.popPose();
    }
    private record Transform(float[] position, float[] rotation, float[] scale) {}
    private record Frame(float time, Map<String, Transform> parts) {}
}
