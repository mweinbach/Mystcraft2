package ports.jurassicraft.fabric.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.model.ModelProviderException;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Standalone mesh tests; run from the repository root with the mapped Minecraft jar on the classpath. */
public final class MachineObjModelTest {
    public static void main(String[] args) throws Exception {
        Path models = Path.of("mods/jurassicraft/src/main/resources/assets/jurassicraft/models/block");
        Map<String, Integer> counts = Map.of("dna_extractor", 90, "dna_sequencer", 66,
                "dna_combinator_hybridizer", 30, "dna_synthesizer", 102,
                "embryonic_machine", 98, "embryo_calcification_machine", 104,
                "incubator", 60, "cleaning_station", 88, "feeder", 66);
        MachineObjMesh extractor = null;
        for (var model : counts.entrySet()) {
            MachineObjMesh mesh;
            try (var reader = Files.newBufferedReader(models.resolve(model.getKey() + ".obj"))) {
                mesh = MachineObjMesh.read(reader);
            }
            check(mesh.faces().size() == model.getValue(), "face count of " + model.getKey());
            if (model.getKey().equals("dna_extractor")) extractor = mesh;
        }
        check(extractor != null, "extractor model exists");
        var face = extractor.faces().get(0);
        var identity = MachineObjModel.bakeFace(face, BlockModelRotation.X0_Y0, value -> value, value -> value);
        check(identity.direction() == Direction.WEST, "original face normal");
        close(identity.data(), 0, 0); close(identity.data(), 1, .1875F); close(identity.data(), 2, .875F);
        close(identity.data(), 4, 10); close(identity.data(), 5, 3); // no vertical UV inversion
        check((identity.data()[7] & 255) == 129, "west normal packed as signed byte");
        var east = MachineObjModel.bakeFace(face, BlockModelRotation.X0_Y90, value -> value, value -> value);
        check(east.direction() == Direction.NORTH, "rotated face normal");
        close(east.data(), 0, .125F); close(east.data(), 1, .1875F); close(east.data(), 2, 0);
        close(east.data(), 4, 10); close(east.data(), 5, 3);
        var down = MachineObjModel.bakeFace(face, BlockModelRotation.X90_Y0, value -> value, value -> value);
        check(down.direction() == Direction.WEST, "X rotation keeps west facing");
        close(down.data(), 0, 0); close(down.data(), 1, .875F); close(down.data(), 2, .8125F);

        var triangle = MachineObjMesh.read(new StringReader("v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nf -3/-3 -2/-2 -1/-1\n"));
        check(triangle.faces().get(0).vertices().size() == 4, "triangle converted to quad");
        check(triangle.faces().get(0).vertices().get(2).equals(triangle.faces().get(0).vertices().get(3)), "triangle last vertex repeated");
        expectInvalid("v NaN 0 0\n");
        expectInvalid("v 0 0 0\nvt 0 0\nf 1/1 2/1 1/1\n");
        var id = new ResourceLocation("jurassicraft", "block/dna_extractor");
        var jsonPath = new ResourceLocation("jurassicraft", "models/block/dna_extractor.json");
        var objPath = new ResourceLocation("jurassicraft", "models/block/dna_extractor.obj");
        Path marker = Path.of("mods/jurassicraft/src/fabric/resources/assets/jurassicraft/models/block/dna_extractor.json");
        String json = Files.readString(marker);
        String obj = Files.readString(models.resolve("dna_extractor.obj"));
        var provided = MachineModels.load(new MemoryResources(Map.of(jsonPath, json, objPath, obj)), id);
        check(provided instanceof MachineObjModel, "marked model is provided");
        check(provided.getMaterials(ignored -> null, new java.util.HashSet<>()).size() == 2, "surface and particle textures are stitched");
        check(MachineModels.load(new MemoryResources(Map.of(jsonPath, "{\"parent\":\"minecraft:block/stone\"}")), id) == null,
                "resource pack can override with vanilla model");
        check(MachineModels.load(new MemoryResources(Map.of(jsonPath, json)), new ResourceLocation("another_mod", "block/dna_extractor")) == null,
                "other namespaces are untouched");
        try {
            MachineModels.load(new MemoryResources(Map.of(jsonPath, json, objPath, "v NaN 0 0")), id);
            throw new AssertionError("Invalid mesh accepted by provider");
        } catch (ModelProviderException expected) { }

        var generated = new ResourceLocation("jurassicraft", "item/dna_extractor");
        var generatedPath = new ResourceLocation("jurassicraft", "models/item/dna_extractor.json");
        check(MachineModels.load(new MemoryResources(Map.of(generatedPath,
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"jurassicraft:items/dna_extractor\"}}")),
                generated) == null, "generated item models stay vanilla block models");
        check(substituteRealItemParents() == 3, "every item that parents a custom machine block is substituted");
        System.out.println("Machine OBJ models and rotations passed");
    }

    /** Item JSON that parents a Fabric OBJ block must not be loaded as a BlockModel. */
    private static int substituteRealItemParents() throws Exception {
        Path items = Path.of("mods/jurassicraft/src/main/resources/assets/jurassicraft/models/item");
        Path fabricBlocks = Path.of("mods/jurassicraft/src/fabric/resources/assets/jurassicraft/models/block");
        Path meshes = Path.of("mods/jurassicraft/src/main/resources/assets/jurassicraft/models/block");
        String vanillaBlock = """
                {"gui_light":"side","display":{"gui":{"rotation":[30,225,0],"translation":[0,0,0],"scale":[0.625,0.625,0.625]},"ground":{"rotation":[0,0,0],"translation":[0,3,0],"scale":[0.25,0.25,0.25]},"fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[0.5,0.5,0.5]},"thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[0.375,0.375,0.375]},"firstperson_righthand":{"rotation":[0,45,0],"translation":[0,0,0],"scale":[0.4,0.4,0.4]},"firstperson_lefthand":{"rotation":[0,225,0],"translation":[0,0,0],"scale":[0.4,0.4,0.4]}}}
                """;
        int substituted = 0;
        try (Stream<Path> listing = Files.list(items)) {
            for (Path item : listing.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject json = JsonParser.parseReader(Files.newBufferedReader(item)).getAsJsonObject();
                if (!json.has("parent")) continue;
                String parent = json.get("parent").getAsString();
                if (!parent.startsWith("jurassicraft:block/")) continue;
                String name = parent.substring("jurassicraft:block/".length());
                Path marker = fabricBlocks.resolve(name + ".json");
                if (!Files.isRegularFile(marker)) continue;
                JsonObject block = JsonParser.parseReader(Files.newBufferedReader(marker)).getAsJsonObject();
                if (!block.has("jurassicraft_obj")) continue;
                String mesh = new ResourceLocation(block.get("jurassicraft_obj").getAsString()).getPath();
                Map<ResourceLocation, String> files = new HashMap<>();
                String fileName = item.getFileName().toString();
                files.put(new ResourceLocation("jurassicraft", "models/item/" + fileName), Files.readString(item));
                files.put(new ResourceLocation("jurassicraft", "models/block/" + name + ".json"), Files.readString(marker));
                files.put(new ResourceLocation("jurassicraft", mesh), Files.readString(meshes.resolve(Path.of(mesh).getFileName())));
                files.put(new ResourceLocation("minecraft", "models/block/block.json"), vanillaBlock);
                UnbakedModel loaded = MachineModels.load(new MemoryResources(files),
                        new ResourceLocation("jurassicraft", "item/" + fileName.substring(0, fileName.length() - ".json".length())));
                check(loaded instanceof MachineObjModel, fileName + " must not parent a non-block model");
                check(Math.abs(((MachineObjModel) loaded).transforms().gui.scale.x() - 0.625F) < 0.0001F,
                        fileName + " keeps the vanilla block gui scale");
                substituted++;
            }
        }
        return substituted;
    }

    private static void expectInvalid(String obj) throws Exception {
        try {
            MachineObjMesh.read(new StringReader(obj));
            throw new AssertionError("Invalid OBJ accepted");
        } catch (IOException expected) { }
    }

    private static void close(int[] data, int offset, float expected) {
        float actual = Float.intBitsToFloat(data[offset]);
        check(Math.abs(actual - expected) < 0.0001F, "coordinate " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record MemoryResources(Map<ResourceLocation, String> files) implements ResourceManager {
        @Override public Optional<Resource> getResource(ResourceLocation id) {
            String content = files.get(id);
            return content == null ? Optional.empty() : Optional.of(new Resource("test", () ->
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
        }
        @Override public Set<String> getNamespaces() { return Set.of("jurassicraft"); }
        @Override public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).stream().toList(); }
        @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) { return Map.of(); }
        @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) { return Map.of(); }
        @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
    }
}
