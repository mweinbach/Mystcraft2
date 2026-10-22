package ports.jurassicraft.client;

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
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;

/**
 * Draws selected cubes from an original Tabula model on top of the baked block.
 * The pose matches {@code JurassicraftVanillaTabulaModel}: blockstate rotation about
 * the block center, then translate to {@code (0.5, 1.5, 0.5)} and scale by 1/16.
 */
public final class TabulaParts {
    private final List<ModelPart> roots = new ArrayList<>();
    private final Map<String, ModelPart> parts = new LinkedHashMap<>();
    private final float[] scale;

    public TabulaParts(String modelPath) {
        ResourceLocation path = new ResourceLocation(PortMod.MOD_ID, modelPath);
        try (var stream = Minecraft.getInstance().getResourceManager().getResource(path).orElseThrow().open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            int width = data.get("textureWidth").getAsInt();
            int height = data.get("textureHeight").getAsInt();
            scale = array(data.getAsJsonArray("scale"));
            for (var cube : data.getAsJsonArray("cubes")) roots.add(part(cube.getAsJsonObject(), width, height));
            for (ModelPart part : parts.values()) part.skipDraw = true;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not load machine part model " + path, e);
        }
    }

    /** Shows only these cubes. Parents stay in the transform chain and are not drawn. */
    public void show(String... names) {
        for (ModelPart part : parts.values()) part.skipDraw = true;
        for (String name : names) {
            ModelPart part = parts.get(name);
            if (part == null) throw new IllegalArgumentException("Missing machine cube " + name);
            part.skipDraw = false;
        }
    }

    public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay, Direction facing, boolean mirrorX) {
        int y = switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        pose.pushPose();
        pose.translate(.5, .5, .5);
        pose.mulPose(BlockModelRotation.by(0, y).getRotation().getLeftRotation());
        pose.translate(0, 1, 0);
        pose.scale(mirrorX ? -scale[0] : scale[0], -scale[1], scale[2]);
        for (ModelPart part : roots) part.render(pose, buffer, light, overlay);
        pose.popPose();
    }

    private ModelPart part(JsonObject cube, int texWidth, int texHeight) {
        float[] dimensions = array(cube.getAsJsonArray("dimensions"));
        float[] offset = array(cube.getAsJsonArray("offset"));
        float[] uv = array(cube.getAsJsonArray("txOffset"));
        float[] position = array(cube.getAsJsonArray("position"));
        float[] rotation = array(cube.getAsJsonArray("rotation"));
        float[] cubeScale = array(cube.getAsJsonArray("scale"));
        float inflation = cube.has("mcScale") ? cube.get("mcScale").getAsFloat() : 0;
        boolean mirror = cube.has("txMirror") && cube.get("txMirror").getAsBoolean();
        var boxes = List.of(new ModelPart.Cube((int) uv[0], (int) uv[1], offset[0], offset[1], offset[2],
                dimensions[0], dimensions[1], dimensions[2], inflation, inflation, inflation, mirror, texWidth, texHeight));
        Map<String, ModelPart> children = new LinkedHashMap<>();
        int index = 0;
        for (var child : cube.getAsJsonArray("children")) {
            children.put(Integer.toString(index++), part(child.getAsJsonObject(), texWidth, texHeight));
        }
        ModelPart result = new ModelPart(boxes, children);
        result.setPos(position[0], position[1], position[2]);
        result.setRotation((float) Math.toRadians(rotation[0]), (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[2]));
        result.xScale = cubeScale[0];
        result.yScale = cubeScale[1];
        result.zScale = cubeScale[2];
        parts.put(cube.get("name").getAsString(), result);
        return result;
    }

    private static float[] array(JsonArray input) {
        float[] values = new float[input.size()];
        for (int i = 0; i < values.length; i++) values[i] = input.get(i).getAsFloat();
        return values;
    }
}
