/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model.geometry;

import com.mojang.datafixers.util.Pair;
import com.mojang.math.Transformation;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The Forge-only parts of a block model JSON ({@code loader} geometry, {@code render_type}, {@code visibility},
 * {@code transform}), inherited from parent models like the vanilla parts are.
 */
public class BlockGeometryBakingContext implements IGeometryBakingContext {

    public final BlockModel owner;
    public final VisibilityData visibilityData = new VisibilityData();
    @Nullable
    private IUnbakedGeometry<?> customGeometry;
    @Nullable
    private Transformation rootTransform;
    @Nullable
    private ResourceLocation renderTypeHint;
    @Nullable
    private ResourceLocation renderTypeFastHint;
    private boolean gui3d = true;

    public BlockGeometryBakingContext(BlockModel owner) {
        this.owner = owner;
    }

    @Nullable
    private BlockGeometryBakingContext parent() {
        return owner.parent == null ? null : owner.parent.getCustomData();
    }

    @Override
    public String getModelName() {
        return owner.name;
    }

    public boolean hasCustomGeometry() {
        return getCustomGeometry() != null;
    }

    @Nullable
    public IUnbakedGeometry<?> getCustomGeometry() {
        BlockGeometryBakingContext parent = parent();
        return customGeometry == null && parent != null ? parent.getCustomGeometry() : customGeometry;
    }

    public void setCustomGeometry(IUnbakedGeometry<?> geometry) {
        this.customGeometry = geometry;
    }

    @Override
    public boolean isComponentVisible(String part, boolean fallback) {
        BlockGeometryBakingContext parent = parent();
        if (visibilityData.hasCustomVisibility(part)) {
            return visibilityData.isVisible(part, fallback);
        }
        return parent == null ? fallback : parent.isComponentVisible(part, fallback);
    }

    @Override
    public boolean hasMaterial(String name) {
        return owner.hasTexture(name);
    }

    @Override
    public Material getMaterial(String name) {
        return owner.getMaterial(name);
    }

    @Override
    public boolean isGui3d() {
        return gui3d;
    }

    @Override
    public boolean useBlockLight() {
        return owner.getGuiLight().lightLikeBlock();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return owner.hasAmbientOcclusion();
    }

    @Override
    public ItemTransforms getTransforms() {
        return owner.getTransforms();
    }

    @Override
    public Transformation getRootTransform() {
        if (rootTransform != null) {
            return rootTransform;
        }
        BlockGeometryBakingContext parent = parent();
        return parent == null ? Transformation.identity() : parent.getRootTransform();
    }

    public void setRootTransform(Transformation rootTransform) {
        this.rootTransform = rootTransform;
    }

    @Nullable
    @Override
    public ResourceLocation getRenderTypeHint() {
        if (renderTypeHint != null) {
            return renderTypeHint;
        }
        BlockGeometryBakingContext parent = parent();
        return parent == null ? null : parent.getRenderTypeHint();
    }

    @Nullable
    @Override
    public ResourceLocation getRenderTypeFastHint() {
        if (renderTypeFastHint != null) {
            return renderTypeFastHint;
        }
        BlockGeometryBakingContext parent = parent();
        return parent == null ? null : parent.getRenderTypeFastHint();
    }

    public void setRenderTypeHint(ResourceLocation renderTypeHint) {
        this.renderTypeHint = renderTypeHint;
    }

    public void setRenderTypeFastHint(ResourceLocation renderTypeFastHint) {
        this.renderTypeFastHint = renderTypeFastHint;
    }

    public void setGui3d(boolean gui3d) {
        this.gui3d = gui3d;
    }

    public void copyFrom(BlockGeometryBakingContext other) {
        this.customGeometry = other.customGeometry;
        this.rootTransform = other.rootTransform;
        this.visibilityData.copyFrom(other.visibilityData);
        this.renderTypeHint = other.renderTypeHint;
        this.renderTypeFastHint = other.renderTypeFastHint;
        this.gui3d = other.gui3d;
    }

    public Collection<Material> getTextureDependencies(Function<ResourceLocation, UnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors) {
        IUnbakedGeometry<?> geometry = getCustomGeometry();
        return geometry == null ? Set.of() : geometry.getMaterials(this, modelGetter, missingTextureErrors);
    }

    public BakedModel bake(ModelBakery bakery, Function<Material, TextureAtlasSprite> bakedTextureGetter, ModelState modelTransform, ItemOverrides overrides,
          ResourceLocation modelLocation) {
        IUnbakedGeometry<?> geometry = getCustomGeometry();
        if (geometry == null) {
            throw new IllegalStateException("Can not use custom baking without custom geometry");
        }
        return geometry.bake(this, bakery, bakedTextureGetter, modelTransform, overrides, modelLocation);
    }

    public static class VisibilityData {

        private final Map<String, Boolean> visibilityData = new HashMap<>();

        public boolean hasCustomVisibility(String part) {
            return visibilityData.containsKey(part);
        }

        public boolean isVisible(String part, boolean fallback) {
            return visibilityData.getOrDefault(part, fallback);
        }

        public void setVisibilityState(String partName, boolean type) {
            visibilityData.put(partName, type);
        }

        public void copyFrom(VisibilityData visibilityData) {
            this.visibilityData.clear();
            this.visibilityData.putAll(visibilityData.visibilityData);
        }
    }
}
