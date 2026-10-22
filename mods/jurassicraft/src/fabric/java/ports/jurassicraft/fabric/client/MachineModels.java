package ports.jurassicraft.fabric.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.util.Set;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.client.model.ModelProviderException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Client-only model provider for the nine generated Tabula machine meshes. */
public final class MachineModels {
    private static final String MOD_ID = "jurassicraft";
    private static final Set<String> MODELS = Set.of("dna_extractor", "dna_sequencer", "dna_combinator_hybridizer",
            "dna_synthesizer", "embryonic_machine", "embryo_calcification_machine", "incubator", "cleaning_station", "feeder");

    private MachineModels() {}

    public static void init() {
        ModelLoadingRegistry.INSTANCE.registerResourceProvider(manager -> (id, context) -> load(manager, id));
        for (String name : MODELS) {
            ResourceLocation id = new ResourceLocation(MOD_ID, name);
            if (Registry.BLOCK.containsKey(id)) {
                BlockRenderLayerMap.INSTANCE.putBlock(Registry.BLOCK.get(id), name.equals("dna_synthesizer")
                        ? RenderType.translucent() : RenderType.cutout());
            }
        }
    }

    static UnbakedModel load(ResourceManager resources, ResourceLocation id) throws ModelProviderException {
        if (!id.getNamespace().equals(MOD_ID)) return null;
        // Item JSON that parents one of these blocks is still a vanilla BlockModel. BlockModel.getMaterials
        // rejects a parent that is not itself a BlockModel, which aborts the reload before the title screen.
        if (id.getPath().startsWith("item/")) return loadItem(resources, id);
        return loadBlock(resources, id);
    }

    /** The item model is replaced wholesale so it never resolves the custom parent. */
    private static UnbakedModel loadItem(ResourceManager resources, ResourceLocation id) throws ModelProviderException {
        ResourceLocation jsonPath = new ResourceLocation(id.getNamespace(), "models/" + id.getPath() + ".json");
        if (resources.getResource(jsonPath).isEmpty()) return null;
        JsonObject model;
        try (Reader json = resources.openAsReader(jsonPath)) {
            var parsed = JsonParser.parseReader(json);
            if (!parsed.isJsonObject()) return null;
            model = parsed.getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return null;
        }
        if (!model.has("parent") || !model.get("parent").isJsonPrimitive()) return null;
        ResourceLocation parent;
        try {
            parent = new ResourceLocation(model.get("parent").getAsString());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return loadBlock(resources, parent);
    }

    private static UnbakedModel loadBlock(ResourceManager resources, ResourceLocation id) throws ModelProviderException {
        if (!id.getNamespace().equals(MOD_ID) || !id.getPath().startsWith("block/")
                || !MODELS.contains(id.getPath().substring("block/".length()))) return null;
        ResourceLocation jsonPath = new ResourceLocation(id.getNamespace(), "models/" + id.getPath() + ".json");
        if (resources.getResource(jsonPath).isEmpty()) return null;
        try (Reader json = resources.openAsReader(jsonPath)) {
            JsonObject model = JsonParser.parseReader(json).getAsJsonObject();
            // A resource pack can replace this marker with a normal vanilla JSON model.
            if (!model.has("jurassicraft_obj")) return null;
            ResourceLocation obj = new ResourceLocation(model.get("jurassicraft_obj").getAsString());
            JsonObject textures = model.getAsJsonObject("textures");
            ResourceLocation surface = new ResourceLocation(textures.get("surface").getAsString());
            ResourceLocation particle = new ResourceLocation(textures.get("particle").getAsString());
            try (Reader mesh = resources.openAsReader(obj)) {
                return new MachineObjModel(MachineObjMesh.read(mesh), surface, particle, blockDisplay(resources));
            }
        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException e) {
            throw new ModelProviderException("Cannot load JurassiCraft machine model " + id, e);
        }
    }

    /** Same display transforms Forge inherits from {@code minecraft:block/block}. */
    private static ItemTransforms blockDisplay(ResourceManager resources) throws ModelProviderException {
        ResourceLocation id = new ResourceLocation("minecraft", "models/block/block.json");
        if (resources.getResource(id).isEmpty()) return ItemTransforms.NO_TRANSFORMS;
        try (Reader reader = resources.openAsReader(id)) {
            return BlockModel.fromStream(reader).getTransforms();
        } catch (IOException | JsonParseException e) {
            throw new ModelProviderException("Cannot read vanilla block display transforms", e);
        }
    }
}
