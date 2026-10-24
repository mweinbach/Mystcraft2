/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.client.ChunkRenderTypeSet;
import ports.projecte.lib.client.model.data.ModelData;
import ports.projecte.lib.client.model.geometry.IGeometryBakingContext;
import ports.projecte.lib.client.model.geometry.IGeometryLoader;
import ports.projecte.lib.client.model.geometry.IUnbakedGeometry;

/**
 * The {@code forge:separate_transforms} model: a base model, replaced by another model for selected display
 * transforms. Perspective keys are the lower-case names of {@link ItemTransforms.TransformType}.
 */
public class SeparateTransformsModel implements IUnbakedGeometry<SeparateTransformsModel> {

    private final BlockModel baseModel;
    private final ImmutableMap<ItemTransforms.TransformType, BlockModel> perspectives;

    public SeparateTransformsModel(BlockModel baseModel, ImmutableMap<ItemTransforms.TransformType, BlockModel> perspectives) {
        this.baseModel = baseModel;
        this.perspectives = perspectives;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
          ItemOverrides overrides, ResourceLocation modelLocation) {
        ImmutableMap.Builder<ItemTransforms.TransformType, BakedModel> bakedPerspectives = ImmutableMap.builder();
        for (Map.Entry<ItemTransforms.TransformType, BlockModel> entry : perspectives.entrySet()) {
            BlockModel model = entry.getValue();
            bakedPerspectives.put(entry.getKey(), model.bake(bakery, model, spriteGetter, modelState, modelLocation, context.useBlockLight()));
        }
        return new Baked(context.useAmbientOcclusion(), context.isGui3d(), context.useBlockLight(), spriteGetter.apply(context.getMaterial("particle")), overrides,
              baseModel.bake(bakery, baseModel, spriteGetter, modelState, modelLocation, context.useBlockLight()), bakedPerspectives.build());
    }

    @Override
    public Collection<Material> getMaterials(IGeometryBakingContext context, Function<ResourceLocation, UnbakedModel> modelGetter,
          Set<Pair<String, String>> missingTextureErrors) {
        Set<Material> materials = new HashSet<>();
        if (context.hasMaterial("particle")) {
            materials.add(context.getMaterial("particle"));
        }
        materials.addAll(baseModel.getMaterials(modelGetter, missingTextureErrors));
        for (BlockModel model : perspectives.values()) {
            materials.addAll(model.getMaterials(modelGetter, missingTextureErrors));
        }
        return materials;
    }

    public static class Baked implements IDynamicBakedModel {

        private final boolean isAmbientOcclusion;
        private final boolean isGui3d;
        private final boolean isSideLit;
        private final TextureAtlasSprite particle;
        private final ItemOverrides overrides;
        //Not final: Mekanism replaces these reflectively to light the models
        private BakedModel baseModel;
        private ImmutableMap<ItemTransforms.TransformType, BakedModel> perspectives;

        public Baked(boolean isAmbientOcclusion, boolean isGui3d, boolean isSideLit, TextureAtlasSprite particle, ItemOverrides overrides, BakedModel baseModel,
              ImmutableMap<ItemTransforms.TransformType, BakedModel> perspectives) {
            this.isAmbientOcclusion = isAmbientOcclusion;
            this.isGui3d = isGui3d;
            this.isSideLit = isSideLit;
            this.particle = particle;
            this.overrides = overrides;
            this.baseModel = baseModel;
            this.perspectives = perspectives;
        }

        @NotNull
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data,
              @Nullable RenderType renderType) {
            return baseModel.getQuads(state, side, rand, data, renderType);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return isAmbientOcclusion;
        }

        @Override
        public boolean isGui3d() {
            return isGui3d;
        }

        @Override
        public boolean usesBlockLight() {
            return isSideLit;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return particle;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public BakedModel applyTransform(ItemTransforms.TransformType transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
            BakedModel perspective = perspectives.get(transformType);
            if (perspective != null) {
                return perspective.applyTransform(transformType, poseStack, applyLeftHandTransform);
            }
            return baseModel.applyTransform(transformType, poseStack, applyLeftHandTransform);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
            return baseModel.getRenderTypes(state, rand, data);
        }
    }

    public static final class Loader implements IGeometryLoader<SeparateTransformsModel> {

        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public SeparateTransformsModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) {
            BlockModel baseModel = deserializationContext.deserialize(GsonHelper.getAsJsonObject(jsonObject, "base"), BlockModel.class);
            JsonObject perspectiveData = GsonHelper.getAsJsonObject(jsonObject, "perspectives");
            ImmutableMap.Builder<ItemTransforms.TransformType, BlockModel> perspectives = ImmutableMap.builder();
            for (ItemTransforms.TransformType type : ItemTransforms.TransformType.values()) {
                String key = type.name().toLowerCase(Locale.ROOT);
                if (perspectiveData.has(key)) {
                    perspectives.put(type, deserializationContext.deserialize(GsonHelper.getAsJsonObject(perspectiveData, key), BlockModel.class));
                }
            }
            return new SeparateTransformsModel(baseModel, perspectives.build());
        }
    }
}
