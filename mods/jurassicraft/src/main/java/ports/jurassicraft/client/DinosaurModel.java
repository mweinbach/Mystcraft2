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
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurEntity;
import ports.jurassicraft.entity.DinosaurLookTracking;
import ports.jurassicraft.client.DinosaurPoseEngine.Frame;
import ports.jurassicraft.client.DinosaurPoseEngine.Transform;

/** A 1.19 renderer for the original Tabula geometry and pose keyframes. */
public final class DinosaurModel extends EntityModel<DinosaurEntity> {
    private final List<ModelPart> roots = new ArrayList<>();
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final Map<String, List<Frame>> animations = new HashMap<>();
    private final Map<DinosaurEntity, DinosaurPoseEngine.State> animationStates = new java.util.WeakHashMap<>();
    private final DinosaurPoseEngine poseEngine;
    private final List<ModelPart> lookParts = new ArrayList<>();
    private float lookDivisor = 1;
    private final float[] modelScale;

    public DinosaurModel(Species species) { this(species, (String)null); }
    /** Static skeleton models can be rendered directly without calling setupAnim. */
    public DinosaurModel(Species species, boolean skeleton) { this(species, skeleton ? "idle" : null); }
    public DinosaurModel(Species species, String skeletonPose) { this(species, skeletonPose, false); }
    public static DinosaurModel egg(Species species) { return new DinosaurModel(species, null, true); }
    private DinosaurModel(Species species, String skeletonPose, boolean egg) {
        String suffix = egg ? "_egg" : skeletonPose == null ? "" : "_skeleton" + (skeletonPose.equals("idle") ? "" : "_" + skeletonPose);
        ResourceLocation path = new ResourceLocation(PortMod.MOD_ID, "models/entity_port/" + species.id() + suffix + ".json");
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
            if (skeletonPose == null && !egg) loadLookParts(species);
            poseEngine = new DinosaurPoseEngine(animations.get("IDLE").get(0).parts(), animations, species == Species.COELACANTH);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not load original dinosaur model " + path, e);
        }
    }
    private void loadLookParts(Species species) throws IOException {
        ResourceLocation path = new ResourceLocation(PortMod.MOD_ID, "models/entity_port/head_tracking.json");
        try (var stream = Minecraft.getInstance().getResourceManager().getResource(path).orElseThrow().open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject(species.id());
            if (data == null) return;
            lookDivisor = data.get("divisor").getAsFloat();
            for (var id : data.getAsJsonArray("parts")) {
                ModelPart part = parts.get(id.getAsString());
                if (part == null) throw new IllegalStateException("Missing look part " + id.getAsString() + " for " + species);
                lookParts.add(part);
            }
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
    @Override public void setupAnim(DinosaurEntity entity, float limbSwing, float amount, float age, float yaw, float pitch) {
        String combat = switch (entity.combatAnimation()) {
            case SPIT -> "DILOPHOSAURUS_SPIT";
            case PREPARE_LEAP -> "PREPARE_LEAP";
            case LEAP -> "LEAP";
            case LEAP_LAND -> "LEAP_LAND";
            default -> null;
        };
        if (entity.isCombatPoseInterrupted()) combat = null;
        String pose = entity.poseAnimation().isEmpty() ? null : entity.poseAnimation();
        String action;
        int actionStart;
        float actionElapsed;
        if (entity.isCarcass() || entity.deathTime > 0) {
            action = "DYING";
            actionStart = entity.isCarcass() ? entity.carcassAnimationStart() : entity.tickCount - entity.deathTime;
            actionElapsed = entity.isCarcass() ? Math.max(0, age - actionStart) : entity.deathTime;
        } else if (entity.isDinosaurSleeping() || entity.isOrderedToSit() || entity.isNaturallyResting()) {
            action = entity.isDinosaurSleeping() ? "SLEEPING" : "RESTING";
            actionStart = 0;
            actionElapsed = 0;
        } else if (entity.isInjuryAnimating()) {
            action = "INJURED";
            actionStart = entity.injuryAnimationStart();
            actionElapsed = entity.injuryAnimationTime(age);
        } else if (entity.isForcedPose() || (combat == null && pose != null)) {
            action = pose;
            actionStart = entity.poseAnimationStart();
            actionElapsed = entity.isBirthing() && !entity.isForcedPose()
                    ? entity.birthAnimationTime(age) : Math.max(0, age - actionStart);
        } else if (combat != null) {
            action = combat;
            actionStart = entity.combatAnimationStart();
            actionElapsed = Math.max(0, age - actionStart);
        } else if (entity.isGliding()) {
            action = "GLIDING";
            actionStart = 0;
            actionElapsed = 0;
        } else if (entity.swingTime > 0) {
            action = "ATTACKING";
            actionStart = entity.tickCount - entity.swingTime;
            actionElapsed = entity.swingTime;
        } else {
            action = "IDLE";
            actionStart = 0;
            actionElapsed = 0;
        }
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        boolean moving = dx * dx + dz * dz > .001;
        boolean fluid = entity.isInWater() || entity.isInLava();
        String movement = entity.isClimbingTree() ? "CLIMBING"
                : !moving ? "IDLE"
                : fluid && !entity.isOnGround() && animations.containsKey("SWIMMING") ? "SWIMMING"
                : entity.isAnimationRunning() && animations.containsKey("RUNNING") ? "RUNNING" : "WALKING";
        var state = animationStates.computeIfAbsent(entity, ignored -> poseEngine.newState());
        var input = new DinosaurPoseEngine.Input(action, actionStart, actionElapsed, movement, moving, amount,
                fluid, !entity.isInWater(), entity.isCarcass());
        for (var entry : poseEngine.sample(state, input, age).entrySet()) {
            ModelPart part = parts.get(entry.getKey());
            if (part == null) continue;
            Transform value = entry.getValue();
            part.setPos(value.position()[0], value.position()[1], value.position()[2]);
            part.setRotation((float)Math.toRadians(value.rotation()[0]),
                    (float)Math.toRadians(value.rotation()[1]), (float)Math.toRadians(value.rotation()[2]));
            part.xScale = value.scale()[0]; part.yScale = value.scale()[1]; part.zScale = value.scale()[2];
        }
        float yawAmount = DinosaurLookTracking.yaw(yaw, lookDivisor, lookParts.size());
        float pitchAmount = DinosaurLookTracking.pitch(pitch, lookDivisor, lookParts.size());
        for (ModelPart part : lookParts) {
            part.yRot += yawAmount;
            part.xRot += pitchAmount;
        }
    }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        pose.pushPose();
        pose.scale(modelScale[0], modelScale[1], modelScale[2]);
        for (ModelPart root : roots) root.render(pose, buffer, light, overlay, red, green, blue, alpha);
        pose.popPose();
    }
}
