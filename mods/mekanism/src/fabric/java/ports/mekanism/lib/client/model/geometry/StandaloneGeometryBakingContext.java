/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model.geometry;

import com.google.common.base.Predicates;
import com.mojang.math.Transformation;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A baking context that is not tied to a model file, configured with a builder.
 */
public class StandaloneGeometryBakingContext implements IGeometryBakingContext {

    public static final ResourceLocation LOCATION = new ResourceLocation("forge", "standalone");
    public static final StandaloneGeometryBakingContext INSTANCE = create(LOCATION);

    private final ResourceLocation modelName;
    private final Predicate<String> materialCheck;
    private final Function<String, Material> materialLookup;
    private final boolean isGui3d;
    private final boolean useBlockLight;
    private final boolean useAmbientOcclusion;
    private final ItemTransforms transforms;
    private final Transformation rootTransform;
    @Nullable
    private final ResourceLocation renderTypeHint;
    @Nullable
    private final ResourceLocation renderTypeFastHint;
    private final BiPredicate<String, Boolean> visibilityTest;

    private StandaloneGeometryBakingContext(Builder builder, ResourceLocation modelName) {
        this.modelName = modelName;
        this.materialCheck = builder.materialCheck;
        this.materialLookup = builder.materialLookup;
        this.isGui3d = builder.isGui3d;
        this.useBlockLight = builder.useBlockLight;
        this.useAmbientOcclusion = builder.useAmbientOcclusion;
        this.transforms = builder.transforms;
        this.rootTransform = builder.rootTransform;
        this.renderTypeHint = builder.renderTypeHint;
        this.renderTypeFastHint = builder.renderTypeFastHint;
        this.visibilityTest = builder.visibilityTest;
    }

    public static StandaloneGeometryBakingContext create(ResourceLocation modelName) {
        return builder().build(modelName);
    }

    public static StandaloneGeometryBakingContext create(Map<String, ResourceLocation> textures) {
        return create(LOCATION, textures);
    }

    public static StandaloneGeometryBakingContext create(ResourceLocation modelName, Map<String, ResourceLocation> textures) {
        return builder().withTextures(textures, MissingTextureAtlasSprite.getLocation()).build(modelName);
    }

    @Override
    public String getModelName() {
        return modelName.toString();
    }

    @Override
    public boolean hasMaterial(String name) {
        return materialCheck.test(name);
    }

    @Override
    public Material getMaterial(String name) {
        return materialLookup.apply(name);
    }

    @Override
    public boolean isGui3d() {
        return isGui3d;
    }

    @Override
    public boolean useBlockLight() {
        return useBlockLight;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return useAmbientOcclusion;
    }

    @Override
    public ItemTransforms getTransforms() {
        return transforms;
    }

    @Override
    public Transformation getRootTransform() {
        return rootTransform;
    }

    @Nullable
    @Override
    public ResourceLocation getRenderTypeHint() {
        return renderTypeHint;
    }

    @Nullable
    @Override
    public ResourceLocation getRenderTypeFastHint() {
        return renderTypeFastHint;
    }

    @Override
    public boolean isComponentVisible(String component, boolean fallback) {
        return visibilityTest.test(component, fallback);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(IGeometryBakingContext parent) {
        return new Builder(parent);
    }

    public static final class Builder {

        private static final Material NO_MATERIAL = new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation());

        private Predicate<String> materialCheck = Predicates.alwaysFalse();
        private Function<String, Material> materialLookup = name -> NO_MATERIAL;
        private boolean isGui3d = true;
        private boolean useBlockLight = true;
        private boolean useAmbientOcclusion = true;
        private ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
        private Transformation rootTransform = Transformation.identity();
        @Nullable
        private ResourceLocation renderTypeHint;
        @Nullable
        private ResourceLocation renderTypeFastHint;
        private BiPredicate<String, Boolean> visibilityTest = (component, fallback) -> fallback;

        private Builder() {
        }

        private Builder(IGeometryBakingContext parent) {
            this.materialCheck = parent::hasMaterial;
            this.materialLookup = parent::getMaterial;
            this.isGui3d = parent.isGui3d();
            this.useBlockLight = parent.useBlockLight();
            this.useAmbientOcclusion = parent.useAmbientOcclusion();
            this.transforms = parent.getTransforms();
            this.rootTransform = parent.getRootTransform();
            this.renderTypeHint = parent.getRenderTypeHint();
            this.renderTypeFastHint = parent.getRenderTypeFastHint();
            this.visibilityTest = parent::isComponentVisible;
        }

        public Builder withTextures(Map<String, ResourceLocation> textures, ResourceLocation defaultTexture) {
            return withTextures(TextureAtlas.LOCATION_BLOCKS, textures, defaultTexture);
        }

        public Builder withTextures(ResourceLocation atlasLocation, Map<String, ResourceLocation> textures, ResourceLocation defaultTexture) {
            this.materialCheck = textures::containsKey;
            this.materialLookup = name -> new Material(atlasLocation, textures.getOrDefault(name, defaultTexture));
            return this;
        }

        public Builder withMaterials(Map<String, Material> materials, Material defaultMaterial) {
            this.materialCheck = materials::containsKey;
            this.materialLookup = name -> materials.getOrDefault(name, defaultMaterial);
            return this;
        }

        public Builder withGui3d(boolean isGui3d) {
            this.isGui3d = isGui3d;
            return this;
        }

        public Builder withUseBlockLight(boolean useBlockLight) {
            this.useBlockLight = useBlockLight;
            return this;
        }

        public Builder withUseAmbientOcclusion(boolean useAmbientOcclusion) {
            this.useAmbientOcclusion = useAmbientOcclusion;
            return this;
        }

        public Builder withTransforms(ItemTransforms transforms) {
            this.transforms = transforms;
            return this;
        }

        public Builder withRootTransform(Transformation rootTransform) {
            this.rootTransform = rootTransform;
            return this;
        }

        public Builder withRenderTypeHint(ResourceLocation renderTypeHint) {
            this.renderTypeHint = renderTypeHint;
            return this;
        }

        public Builder withRenderTypeHint(ResourceLocation renderTypeHint, ResourceLocation renderTypeFastHint) {
            this.renderTypeHint = renderTypeHint;
            this.renderTypeFastHint = renderTypeFastHint;
            return this;
        }

        public Builder withVisibleComponents(Object2BooleanMap<String> parts) {
            this.visibilityTest = (component, fallback) -> parts.containsKey(component) ? parts.getBoolean(component) : fallback;
            return this;
        }

        public StandaloneGeometryBakingContext build(ResourceLocation modelName) {
            return new StandaloneGeometryBakingContext(this, modelName);
        }
    }
}
