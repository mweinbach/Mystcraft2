/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import ports.mekanism.lib.client.NamedRenderTypeManager;
import ports.mekanism.lib.client.RenderTypeGroup;
import ports.mekanism.lib.client.model.geometry.BlockGeometryBakingContext;
import ports.mekanism.lib.client.model.geometry.GeometryLoaderManager;
import ports.mekanism.lib.client.model.geometry.IGeometryLoader;

/**
 * The keys Forge adds to block and item model JSON: {@code loader} picks a custom geometry loader,
 * {@code render_type} names the render type, {@code transform} is the root transform, and {@code visibility}
 * toggles named parts. Blocks of the given namespaces take the chunk render type of their model, as on Forge.
 */
public final class ExtendedBlockModelJson {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<BakedModel, ResourceLocation> RENDER_TYPE_HINTS = Collections.synchronizedMap(new WeakHashMap<>());

    private ExtendedBlockModelJson() {
    }

    public static void read(BlockModel model, JsonObject json, JsonDeserializationContext context) {
        BlockGeometryBakingContext customData = model.getCustomData();
        // A loader the port does not know belongs to another mod or library (see ModelLoaderKey); unlike Forge,
        // the model is left as vanilla read it.
        IGeometryLoader<?> loader = ModelLoaderKey.resolve(json, GeometryLoaderManager::get,
              name -> LOGGER.debug("Model loader {} is not one of Mekanism's; leaving its models to the mod that registers it", name));
        if (loader != null) {
            model.getElements().clear();
            customData.setCustomGeometry(loader.read(json, context));
        }
        if (json.has("transform")) {
            customData.setRootTransform(TransformationJson.parse(json.get("transform")));
        }
        if (json.has("render_type")) {
            customData.setRenderTypeHint(new ResourceLocation(GsonHelper.getAsString(json, "render_type")));
        }
        if (json.has("visibility")) {
            for (Map.Entry<String, JsonElement> part : GsonHelper.getAsJsonObject(json, "visibility").entrySet()) {
                customData.visibilityData.setVisibilityState(part.getKey(), part.getValue().getAsBoolean());
            }
        }
    }

    public static void recordRenderType(BakedModel baked, BlockGeometryBakingContext context) {
        ResourceLocation hint = context.getRenderTypeHint();
        if (hint != null) {
            RENDER_TYPE_HINTS.put(baked, hint);
        }
    }

    @Nullable
    public static RenderTypeGroup renderTypes(BakedModel baked) {
        ResourceLocation hint = RENDER_TYPE_HINTS.get(baked);
        return hint == null ? null : NamedRenderTypeManager.get(hint);
    }

    /**
     * Gives each block of the namespaces the chunk render type its first hinted block state model names.
     */
    public static void applyBlockRenderTypes(Map<ResourceLocation, BakedModel> bakedModels, Set<String> namespaces) {
        for (Block block : Registry.BLOCK) {
            if (!namespaces.contains(Registry.BLOCK.getKey(block).getNamespace())) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BakedModel model = bakedModels.get(BlockModelShaper.stateToModelLocation(state));
                RenderTypeGroup group = model == null ? null : renderTypes(model);
                if (group != null && !group.isEmpty()) {
                    BlockRenderLayerMap.INSTANCE.putBlock(block, group.block());
                    break;
                }
            }
        }
    }
}
