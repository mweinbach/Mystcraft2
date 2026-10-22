package ports.jurassicraft.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.entity.DinosaurTailSway;

/** Per-species tail, neck, breathing, and walk motion from the original animators. */
public final class DinosaurProceduralPose {
    private enum Op { CHAIN, BOB, WALK, SHIFT }

    private record Motion(Op op, int axis, ModelPart[] parts, float speed, float degree, double root,
                          boolean walkDriver, float amount, boolean water, boolean invert, float offset,
                          float weight, boolean bounce) {}

    private final Motion[] motions;
    private final ModelPart[] tail;
    private final ModelPart[] hiddenUntilTarget;
    private final boolean glidePitch;

    private DinosaurProceduralPose(Motion[] motions, ModelPart[] tail, ModelPart[] hiddenUntilTarget, boolean glidePitch) {
        this.motions = motions;
        this.tail = tail;
        this.hiddenUntilTarget = hiddenUntilTarget;
        this.glidePitch = glidePitch;
    }

    public boolean glides() { return glidePitch; }

    public static DinosaurProceduralPose load(Species species, Map<String, ModelPart> parts) throws IOException {
        JsonObject motion = resource("procedural_motion.json").getAsJsonObject(species.id());
        JsonObject names = resource("cube_names.json").getAsJsonObject(species.id());
        if (motion == null || names == null) throw new IllegalStateException("No procedural motion for " + species);
        return parse(motion, names, parts);
    }

    public void apply(float limbSwing, float limbAmount, float age, boolean inWater, float movementScale,
                      DinosaurTailSway sway, float partialTick, boolean paused, boolean showHidden) {
        for (Motion motion : motions) {
            if (motion.water && !inWater) continue;
            float swing = motion.walkDriver ? limbSwing : age;
            float swingAmount = motion.walkDriver ? limbAmount : motion.amount;
            switch (motion.op) {
                case CHAIN -> {
                    float offset = DinosaurChainMath.chainOffset(motion.root, motion.parts.length);
                    for (int index = 0; index < motion.parts.length; index++) {
                        float rotation = DinosaurChainMath.chainRotation(motion.speed, motion.degree, swing,
                                swingAmount, offset, index, movementScale);
                        if (motion.axis == 0) motion.parts[index].xRot += rotation;
                        else motion.parts[index].yRot += rotation;
                    }
                }
                case BOB -> motion.parts[0].y += DinosaurChainMath.bob(motion.speed, motion.degree, motion.bounce,
                        swing, swingAmount, movementScale);
                case WALK -> motion.parts[0].xRot += DinosaurChainMath.walk(motion.speed, motion.degree, motion.invert,
                        motion.offset, motion.weight, swing, swingAmount, movementScale);
                case SHIFT -> motion.parts[0].z -= motion.degree * (float) Math.cos(age * motion.speed);
            }
        }
        if (tail.length > 0) {
            float yaw = sway.radiansPerPart(partialTick, paused, tail.length);
            for (ModelPart part : tail) part.yRot += yaw;
        }
        for (ModelPart part : hiddenUntilTarget) part.skipDraw = !showHidden;
    }

    /** Confirms the packaged tables name real cubes and keep the upstream chain arguments. */
    public static void verifyPackaged() {
        JsonObject motion = classpath("procedural_motion.json");
        JsonObject names = classpath("cube_names.json");
        for (Species species : Species.values()) {
            JsonObject spec = motion.getAsJsonObject(species.id());
            JsonObject cubes = names.getAsJsonObject(species.id());
            if (spec == null || cubes == null) throw new AssertionError("missing procedural motion for " + species);
            for (String name : referenced(spec)) {
                if (!cubes.has(name) || cubes.get(name).getAsString().isEmpty())
                    throw new AssertionError(species + " has no cube named " + name);
            }
        }
        JsonObject raptor = motion.getAsJsonObject("velociraptor");
        if (raptor.getAsJsonArray("tail").size() != 6)
            throw new AssertionError("velociraptor tail chain");
        var microTail = motion.getAsJsonObject("microraptor").getAsJsonArray("tail");
        if (microTail.size() != 4 || !microTail.get(0).getAsString().equals(microTail.get(3).getAsString()))
            throw new AssertionError("microraptor repeats the one Tail1 cube, as the original animator does");
        if (!motion.getAsJsonObject("microraptor").get("glidePitch").getAsBoolean())
            throw new AssertionError("microraptor glide pitch");
        boolean negativeArm = false;
        for (var element : motion.getAsJsonObject("tyrannosaurus").getAsJsonArray("motions")) {
            JsonObject value = element.getAsJsonObject();
            if ("chain".equals(value.get("op").getAsString()) && value.get("speed").getAsFloat() == -.1f)
                negativeArm = true;
        }
        if (!negativeArm) throw new AssertionError("tyrannosaurus arm wave");
        boolean neckRoot = false;
        for (var element : motion.getAsJsonObject("brachiosaurus").getAsJsonArray("motions")) {
            JsonObject value = element.getAsJsonObject();
            if (value.has("root") && value.get("root").getAsDouble() == -3) neckRoot = true;
        }
        if (!neckRoot) throw new AssertionError("brachiosaurus neck root");
        boolean waterJaw = false;
        for (var element : motion.getAsJsonObject("coelacanth").getAsJsonArray("motions")) {
            JsonObject value = element.getAsJsonObject();
            if (value.has("part") && value.has("water") && value.get("water").getAsBoolean()
                    && "Lower jaw front".equals(value.get("part").getAsString())
                    && value.get("weight").getAsFloat() == .5f) waterJaw = true;
        }
        if (!waterJaw) throw new AssertionError("coelacanth jaw");
        if (motion.getAsJsonObject("dilophosaurus").getAsJsonArray("hideUnlessTarget").size() != 4)
            throw new AssertionError("dilophosaurus frills");
    }

    private static List<String> referenced(JsonObject spec) {
        List<String> names = new ArrayList<>();
        if (spec.has("tail")) for (var name : spec.getAsJsonArray("tail")) names.add(name.getAsString());
        if (spec.has("hideUnlessTarget"))
            for (var name : spec.getAsJsonArray("hideUnlessTarget")) names.add(name.getAsString());
        for (var element : spec.getAsJsonArray("motions")) {
            JsonObject motion = element.getAsJsonObject();
            if (motion.has("part")) names.add(motion.get("part").getAsString());
            if (motion.has("parts")) for (var name : motion.getAsJsonArray("parts")) names.add(name.getAsString());
        }
        return names;
    }

    private static DinosaurProceduralPose parse(JsonObject spec, JsonObject names, Map<String, ModelPart> parts) {
        List<Motion> motions = new ArrayList<>();
        for (var element : spec.getAsJsonArray("motions")) motions.add(motion(element.getAsJsonObject(), names, parts));
        return new DinosaurProceduralPose(motions.toArray(Motion[]::new), parts(spec, "tail", names, parts),
                parts(spec, "hideUnlessTarget", names, parts),
                spec.has("glidePitch") && spec.get("glidePitch").getAsBoolean());
    }

    private static Motion motion(JsonObject value, JsonObject names, Map<String, ModelPart> parts) {
        String op = value.get("op").getAsString();
        boolean walk = "walk".equals(value.has("driver") ? value.get("driver").getAsString() : "");
        float amount = value.has("amount") ? value.get("amount").getAsFloat() : 0;
        boolean water = value.has("water") && value.get("water").getAsBoolean();
        return switch (op) {
            case "chain" -> new Motion(Op.CHAIN, "y".equals(value.get("axis").getAsString()) ? 1 : 0,
                    parts(value.getAsJsonArray("parts"), names, parts), value.get("speed").getAsFloat(),
                    value.get("degree").getAsFloat(), value.get("root").getAsDouble(), walk, amount, water,
                    false, 0, 0, false);
            case "bob" -> new Motion(Op.BOB, 0, new ModelPart[] {part(value.get("part").getAsString(), names, parts)},
                    value.get("speed").getAsFloat(), value.get("degree").getAsFloat(), 0, walk, amount, water,
                    false, 0, 0, value.has("bounce") && value.get("bounce").getAsBoolean());
            case "walk" -> new Motion(Op.WALK, 0, new ModelPart[] {part(value.get("part").getAsString(), names, parts)},
                    value.get("speed").getAsFloat(), value.get("degree").getAsFloat(), 0, walk, amount, water,
                    value.get("invert").getAsBoolean(), value.get("offset").getAsFloat(), value.get("weight").getAsFloat(), false);
            case "shiftZ" -> new Motion(Op.SHIFT, 0, new ModelPart[] {part(value.get("part").getAsString(), names, parts)},
                    value.get("frequency").getAsFloat(), value.get("amplitude").getAsFloat(), 0, false, 0, false,
                    false, 0, 0, false);
            default -> throw new IllegalStateException("Unknown procedural motion " + op);
        };
    }

    private static ModelPart[] parts(JsonObject spec, String key, JsonObject names, Map<String, ModelPart> parts) {
        if (!spec.has(key)) return new ModelPart[0];
        return parts(spec.getAsJsonArray(key), names, parts);
    }

    private static ModelPart[] parts(JsonArray ids, JsonObject names, Map<String, ModelPart> parts) {
        ModelPart[] resolved = new ModelPart[ids.size()];
        for (int index = 0; index < resolved.length; index++)
            resolved[index] = part(ids.get(index).getAsString(), names, parts);
        return resolved;
    }

    private static ModelPart part(String name, JsonObject names, Map<String, ModelPart> parts) {
        if (!names.has(name)) throw new IllegalStateException("No dinosaur cube named " + name);
        ModelPart part = parts.get(names.get(name).getAsString());
        if (part == null) throw new IllegalStateException("Dinosaur model is missing " + name);
        return part;
    }

    private static JsonObject resource(String file) throws IOException {
        ResourceLocation path = new ResourceLocation(PortMod.MOD_ID, "models/entity_port/" + file);
        try (var stream = Minecraft.getInstance().getResourceManager().getResource(path).orElseThrow().open();
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static JsonObject classpath(String file) {
        String path = "/assets/jurassicraft/models/entity_port/" + file;
        try (var stream = DinosaurProceduralPose.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}
