/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.ChunkRenderTypeSet;
import ports.mekanism.lib.client.model.data.ModelData;
import ports.mekanism.lib.client.model.data.ModelProperty;
import ports.mekanism.lib.client.model.geometry.IGeometryBakingContext;
import ports.mekanism.lib.client.model.geometry.IGeometryLoader;
import ports.mekanism.lib.client.model.geometry.IUnbakedGeometry;

/**
 * The {@code forge:composite} model: named child block models baked side by side, each with its own render type.
 */
public class CompositeModel implements IUnbakedGeometry<CompositeModel> {

    private final ImmutableMap<String, BlockModel> children;
    private final ImmutableList<String> itemPasses;

    public CompositeModel(ImmutableMap<String, BlockModel> children, ImmutableList<String> itemPasses) {
        this.children = children;
        this.itemPasses = itemPasses;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
          ItemOverrides overrides, ResourceLocation modelLocation) {
        ModelState state = UnbakedGeometryHelper.composeRootTransform(modelState, context.getRootTransform());
        ImmutableMap.Builder<String, BakedModel> bakedChildren = ImmutableMap.builder();
        for (Map.Entry<String, BlockModel> entry : children.entrySet()) {
            if (context.isComponentVisible(entry.getKey(), true)) {
                BlockModel child = entry.getValue();
                bakedChildren.put(entry.getKey(), child.bake(bakery, child, spriteGetter, state, modelLocation, context.useBlockLight()));
            }
        }
        ImmutableMap<String, BakedModel> baked = bakedChildren.build();
        ImmutableList.Builder<BakedModel> passes = ImmutableList.builder();
        for (String name : itemPasses) {
            BakedModel pass = baked.get(name);
            if (pass != null) {
                passes.add(pass);
            }
        }
        return new Baked(context.isGui3d(), context.useBlockLight(), context.useAmbientOcclusion(), spriteGetter.apply(context.getMaterial("particle")),
              context.getTransforms(), overrides, baked, passes.build());
    }

    @Override
    public Collection<Material> getMaterials(IGeometryBakingContext context, Function<ResourceLocation, UnbakedModel> modelGetter,
          Set<Pair<String, String>> missingTextureErrors) {
        Set<Material> materials = new HashSet<>();
        if (context.hasMaterial("particle")) {
            materials.add(context.getMaterial("particle"));
        }
        for (BlockModel child : children.values()) {
            materials.addAll(child.getMaterials(modelGetter, missingTextureErrors));
        }
        return materials;
    }

    @Override
    public Set<String> getConfigurableComponentNames() {
        return children.keySet();
    }

    public static class Baked implements IDynamicBakedModel {

        private final boolean isGui3d;
        private final boolean isSideLit;
        private final boolean isAmbientOcclusion;
        private final TextureAtlasSprite particle;
        private final ItemTransforms transforms;
        private final ItemOverrides overrides;
        private final ImmutableMap<String, BakedModel> children;
        private final ImmutableList<BakedModel> itemPasses;

        public Baked(boolean isGui3d, boolean isSideLit, boolean isAmbientOcclusion, TextureAtlasSprite particle, ItemTransforms transforms, ItemOverrides overrides,
              ImmutableMap<String, BakedModel> children, ImmutableList<BakedModel> itemPasses) {
            this.isGui3d = isGui3d;
            this.isSideLit = isSideLit;
            this.isAmbientOcclusion = isAmbientOcclusion;
            this.particle = particle;
            this.transforms = transforms;
            this.overrides = overrides;
            this.children = children;
            this.itemPasses = itemPasses;
        }

        @NotNull
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data,
              @Nullable RenderType renderType) {
            List<BakedQuad> quads = new ArrayList<>();
            for (Map.Entry<String, BakedModel> entry : children.entrySet()) {
                BakedModel child = entry.getValue();
                if (renderType == null || (state != null && child.getRenderTypes(state, rand, data).contains(renderType))) {
                    quads.addAll(child.getQuads(state, side, rand, Data.resolve(data, entry.getKey()), renderType));
                }
            }
            return quads;
        }

        @NotNull
        @Override
        public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
            Data.Builder builder = Data.builder();
            for (Map.Entry<String, BakedModel> entry : children.entrySet()) {
                builder.with(entry.getKey(), entry.getValue().getModelData(level, pos, state, Data.resolve(modelData, entry.getKey())));
            }
            return modelData.derive().with(Data.PROPERTY, builder.build()).build();
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
            return transforms;
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
            List<ChunkRenderTypeSet> sets = new ArrayList<>();
            for (BakedModel child : children.values()) {
                sets.add(child.getRenderTypes(state, rand, data));
            }
            return ChunkRenderTypeSet.union(sets);
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
            return itemPasses;
        }

        @Nullable
        public BakedModel getPart(String name) {
            return children.get(name);
        }
    }

    /**
     * The model data of each child of a composite model, by child name.
     */
    public static class Data {

        public static final ModelProperty<Data> PROPERTY = new ModelProperty<>();

        private final Map<String, ModelData> partData;

        private Data(Map<String, ModelData> partData) {
            this.partData = partData;
        }

        @Nullable
        public ModelData get(String name) {
            return partData.get(name);
        }

        public static ModelData resolve(ModelData modelData, String name) {
            Data data = modelData.get(PROPERTY);
            if (data == null) {
                return modelData;
            }
            ModelData partData = data.get(name);
            return partData == null ? modelData : partData;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private final Map<String, ModelData> parts = new HashMap<>();

            public Builder with(String name, ModelData data) {
                parts.put(name, data);
                return this;
            }

            public Data build() {
                return new Data(Map.copyOf(parts));
            }
        }
    }

    public static final class Loader implements IGeometryLoader<CompositeModel> {

        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public CompositeModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) {
            ImmutableMap.Builder<String, BlockModel> childrenBuilder = ImmutableMap.builder();
            List<String> itemPasses = new ArrayList<>();
            for (String key : List.of("children", "parts")) {
                if (jsonObject.has(key)) {
                    for (Map.Entry<String, JsonElement> entry : jsonObject.getAsJsonObject(key).entrySet()) {
                        childrenBuilder.put(entry.getKey(), deserializationContext.deserialize(entry.getValue(), BlockModel.class));
                        itemPasses.add(entry.getKey());
                    }
                }
            }
            ImmutableMap<String, BlockModel> children = childrenBuilder.build();
            if (children.isEmpty()) {
                throw new JsonParseException("Composite model requires a \"children\" element with at least one element.");
            }
            if (jsonObject.has("item_render_order")) {
                itemPasses.clear();
                for (JsonElement element : jsonObject.getAsJsonArray("item_render_order")) {
                    String name = element.getAsString();
                    if (!children.containsKey(name)) {
                        throw new JsonParseException("Specified \"" + name + "\" in \"item_render_order\", but that is not a child of this model.");
                    }
                    itemPasses.add(name);
                }
            }
            return new CompositeModel(children, ImmutableList.copyOf(itemPasses));
        }
    }
}
