/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.client.ChunkRenderTypeSet;
import ports.projecte.lib.client.RenderTypeGroup;
import ports.projecte.lib.client.model.data.ModelData;
import ports.projecte.lib.client.model.geometry.IGeometryBakingContext;

/**
 * A static baked model with an optional render type group, the result of baking element and OBJ geometry.
 */
public class QuadListModel implements IDynamicBakedModel {

    private final List<BakedQuad> unculled;
    private final Map<Direction, List<BakedQuad>> culled;
    private final boolean ambientOcclusion;
    private final boolean gui3d;
    private final boolean blockLight;
    private final TextureAtlasSprite particle;
    private final ItemTransforms transforms;
    private final ItemOverrides overrides;
    private final RenderTypeGroup renderTypes;
    @Nullable
    private final ChunkRenderTypeSet blockRenderTypes;

    private QuadListModel(Builder builder) {
        this.unculled = List.copyOf(builder.unculled);
        this.culled = new EnumMap<>(Direction.class);
        builder.culled.forEach((side, quads) -> this.culled.put(side, List.copyOf(quads)));
        this.ambientOcclusion = builder.ambientOcclusion;
        this.gui3d = builder.gui3d;
        this.blockLight = builder.blockLight;
        this.particle = builder.particle;
        this.transforms = builder.transforms;
        this.overrides = builder.overrides;
        this.renderTypes = builder.renderTypes;
        this.blockRenderTypes = renderTypes.isEmpty() ? null : ChunkRenderTypeSet.of(renderTypes.block());
    }

    public static Builder builder(IGeometryBakingContext context, TextureAtlasSprite particle, ItemOverrides overrides, ItemTransforms transforms) {
        ResourceLocationHint hint = new ResourceLocationHint(context);
        return new Builder(context.useAmbientOcclusion(), context.isGui3d(), context.useBlockLight(), particle, transforms, overrides, hint.renderTypes());
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData extraData,
          @Nullable RenderType renderType) {
        return side == null ? unculled : culled.getOrDefault(side, List.of());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return ambientOcclusion;
    }

    @Override
    public boolean isGui3d() {
        return gui3d;
    }

    @Override
    public boolean usesBlockLight() {
        return blockLight;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleIcon() {
        return particle;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public ItemTransforms getTransforms() {
        return transforms;
    }

    @NotNull
    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return blockRenderTypes == null ? IDynamicBakedModel.super.getRenderTypes(state, rand, data) : blockRenderTypes;
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
        if (renderTypes.isEmpty()) {
            return IDynamicBakedModel.super.getRenderTypes(itemStack, fabulous);
        }
        return List.of(fabulous ? renderTypes.entityFabulous() : renderTypes.entity());
    }

    @Override
    public boolean isVanillaAdapter() {
        return blockRenderTypes == null;
    }

    private record ResourceLocationHint(IGeometryBakingContext context) {

        RenderTypeGroup renderTypes() {
            return context.getRenderTypeHint() == null ? RenderTypeGroup.EMPTY : context.getRenderType(context.getRenderTypeHint());
        }
    }

    public static final class Builder {

        private final List<BakedQuad> unculled = new ArrayList<>();
        private final Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        private final boolean ambientOcclusion;
        private final boolean gui3d;
        private final boolean blockLight;
        private TextureAtlasSprite particle;
        private final ItemTransforms transforms;
        private final ItemOverrides overrides;
        private final RenderTypeGroup renderTypes;

        public Builder(boolean ambientOcclusion, boolean gui3d, boolean blockLight, TextureAtlasSprite particle, ItemTransforms transforms,
              ItemOverrides overrides, RenderTypeGroup renderTypes) {
            this.ambientOcclusion = ambientOcclusion;
            this.gui3d = gui3d;
            this.blockLight = blockLight;
            this.particle = particle;
            this.transforms = transforms;
            this.overrides = overrides;
            this.renderTypes = renderTypes;
        }

        public Builder addCulledFace(Direction side, BakedQuad quad) {
            culled.computeIfAbsent(side, s -> new ArrayList<>()).add(quad);
            return this;
        }

        public Builder addUnculledFace(BakedQuad quad) {
            unculled.add(quad);
            return this;
        }

        public Builder particle(TextureAtlasSprite particle) {
            this.particle = particle;
            return this;
        }

        public BakedModel build() {
            return new QuadListModel(this);
        }
    }
}
