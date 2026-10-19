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
                return new MachineObjModel(MachineObjMesh.read(mesh), surface, particle);
            }
        } catch (IOException | JsonParseException | IllegalStateException | NullPointerException e) {
            throw new ModelProviderException("Cannot load JurassiCraft machine model " + id, e);
        }
    }
}
