/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model.obj;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.client.model.geometry.IGeometryLoader;

/**
 * The {@code forge:obj} geometry loader. Parsed models and material libraries are cached until the next resource
 * reload.
 */
public final class ObjLoader implements IGeometryLoader<ObjModel> {

    public static final ObjLoader INSTANCE = new ObjLoader();

    private final Map<ObjModel.ModelSettings, ObjModel> modelCache = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, ObjMaterialLibrary> materialCache = new ConcurrentHashMap<>();
    @Nullable
    private ResourceManager manager;

    private ObjLoader() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        modelCache.clear();
        materialCache.clear();
        manager = resourceManager;
    }

    private ResourceManager manager() {
        return manager == null ? Minecraft.getInstance().getResourceManager() : manager;
    }

    @Override
    public ObjModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
        if (!jsonObject.has("model")) {
            throw new JsonParseException("OBJ Loader requires a 'model' key that points to a valid .OBJ model.");
        }
        String modelLocation = GsonHelper.getAsString(jsonObject, "model");
        boolean automaticCulling = GsonHelper.getAsBoolean(jsonObject, "automatic_culling", true);
        boolean shadeQuads = GsonHelper.getAsBoolean(jsonObject, "shade_quads", true);
        boolean flipV = GsonHelper.getAsBoolean(jsonObject, "flip_v", false);
        boolean emissiveAmbient = GsonHelper.getAsBoolean(jsonObject, "emissive_ambient", true);
        String mtlOverride = GsonHelper.getAsString(jsonObject, "mtl_override", null);
        return loadModel(new ObjModel.ModelSettings(new ResourceLocation(modelLocation), automaticCulling, shadeQuads, flipV, emissiveAmbient, mtlOverride));
    }

    public ObjModel loadModel(ObjModel.ModelSettings settings) {
        return modelCache.computeIfAbsent(settings, s -> {
            try (BufferedReader reader = manager().openAsReader(s.modelLocation())) {
                return ObjModel.parse(reader, s, this::loadMaterialLibrary);
            } catch (IOException e) {
                throw new RuntimeException("Could not find OBJ model " + s.modelLocation(), e);
            }
        });
    }

    public ObjMaterialLibrary loadMaterialLibrary(ResourceLocation materialLocation) {
        return materialCache.computeIfAbsent(materialLocation, location -> {
            try (BufferedReader reader = manager().openAsReader(location)) {
                return ObjMaterialLibrary.read(reader);
            } catch (IOException e) {
                throw new RuntimeException("Could not find OBJ material library " + location, e);
            }
        });
    }
}
