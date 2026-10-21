/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Quaternion;
import com.mojang.math.Transformation;
import com.mojang.math.Vector3f;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.extensions.common.IClientFluidTypeExtensions;
import ports.mekanism.lib.client.model.geometry.IGeometryBakingContext;
import ports.mekanism.lib.client.model.geometry.IGeometryLoader;
import ports.mekanism.lib.client.model.geometry.IUnbakedGeometry;
import ports.mekanism.lib.fluids.FluidUtil;

/**
 * The {@code forge:fluid_container} item model: a base texture, the contained fluid's still texture clipped to a
 * mask texture, and an optional cover. The fluid layer has tint index 1.
 */
public class DynamicFluidContainerModel implements IUnbakedGeometry<DynamicFluidContainerModel> {

    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    //Layers sit slightly in front of and behind the base layer so they don't z-fight with it
    private static final float FLUID_OFFSET = 0.002F;
    private static final float COVER_OFFSET = 0.004F;
    private static final Transformation UPSIDE_DOWN = new Transformation(null, new Quaternion(0, 0, 1, 0), null, null);

    private final Fluid fluid;
    private final boolean flipGas;
    private final boolean coverIsMask;

    public DynamicFluidContainerModel(Fluid fluid, boolean flipGas, boolean coverIsMask) {
        this.fluid = fluid;
        this.flipGas = flipGas;
        this.coverIsMask = coverIsMask;
    }

    public DynamicFluidContainerModel withFluid(Fluid newFluid) {
        return new DynamicFluidContainerModel(newFluid, flipGas, coverIsMask);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
          ItemOverrides overrides, ResourceLocation modelLocation) {
        Material particleLocation = context.hasMaterial("particle") ? context.getMaterial("particle") : null;
        Material baseLocation = context.hasMaterial("base") ? context.getMaterial("base") : null;
        Material fluidMaskLocation = context.hasMaterial("fluid") ? context.getMaterial("fluid") : null;
        Material coverLocation = context.hasMaterial("cover") ? context.getMaterial("cover") : null;

        TextureAtlasSprite baseSprite = baseLocation == null ? null : spriteGetter.apply(baseLocation);
        Material fluidLocation = fluidStillMaterial(fluid);
        TextureAtlasSprite fluidSprite = fluidLocation == null ? null : spriteGetter.apply(fluidLocation);
        TextureAtlasSprite coverSprite = coverLocation == null ? null : spriteGetter.apply(coverLocation);
        TextureAtlasSprite particleSprite = particleLocation == null ? null : spriteGetter.apply(particleLocation);
        if (particleSprite == null) {
            particleSprite = fluidSprite != null ? fluidSprite : baseSprite;
        }
        if (particleSprite == null) {
            particleSprite = spriteGetter.apply(new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation()));
        }

        ModelState state = modelState;
        if (flipGas && fluid != Fluids.EMPTY && fluid.getFluidType().isLighterThanAir()) {
            state = new SimpleModelState(modelState.getRotation().compose(UPSIDE_DOWN), modelState.isUvLocked());
        }
        state = UnbakedGeometryHelper.composeRootTransform(state, context.getRootTransform());

        QuadListModel.Builder builder = QuadListModel.builder(context, particleSprite, new ContainedFluidOverrideHandler(overrides, bakery, context, this),
              context.getTransforms());
        if (baseSprite != null) {
            addQuads(builder, layerElements(0, baseSprite, 0), baseSprite, state, modelLocation);
        }
        if (fluidMaskLocation != null && fluidSprite != null) {
            TextureAtlasSprite maskSprite = spriteGetter.apply(fluidMaskLocation);
            addQuads(builder, maskedElements(1, maskSprite, FLUID_OFFSET), fluidSprite, state, modelLocation);
        }
        if (coverSprite != null) {
            if (coverIsMask) {
                if (baseSprite != null) {
                    addQuads(builder, maskedElements(2, coverSprite, COVER_OFFSET), baseSprite, state, modelLocation);
                }
            } else {
                addQuads(builder, layerElements(2, coverSprite, COVER_OFFSET), coverSprite, state, modelLocation);
            }
        }
        return builder.build();
    }

    @Nullable
    private static Material fluidStillMaterial(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return null;
        }
        ResourceLocation still = IClientFluidTypeExtensions.of(fluid).getStillTexture();
        return still == null ? null : new Material(TextureAtlas.LOCATION_BLOCKS, still);
    }

    @Override
    public Collection<Material> getMaterials(IGeometryBakingContext context, Function<ResourceLocation, UnbakedModel> modelGetter,
          Set<Pair<String, String>> missingTextureErrors) {
        Set<Material> materials = new HashSet<>();
        for (String name : List.of("particle", "base", "fluid", "cover")) {
            if (context.hasMaterial(name)) {
                materials.add(context.getMaterial(name));
            }
        }
        Material fluidLocation = fluidStillMaterial(fluid);
        if (fluidLocation != null) {
            materials.add(fluidLocation);
        }
        return materials;
    }

    /**
     * The elements vanilla generates for an item layer: a front and back face over the whole texture plus edge faces
     * along its opaque pixels.
     */
    private static List<BlockElement> layerElements(int tintIndex, TextureAtlasSprite sprite, float offset) {
        List<BlockElement> elements = ITEM_MODEL_GENERATOR.processFrames(tintIndex, "layer", sprite);
        if (offset == 0 || elements.isEmpty()) {
            return elements;
        }
        List<BlockElement> offsetElements = new ArrayList<>(elements);
        offsetElements.set(0, offsetFrontBack(elements.get(0), offset));
        return offsetElements;
    }

    /**
     * Elements covering only the opaque pixels of a mask texture, one run of pixels per row, plus the mask's edge
     * faces. Faces take their UVs from the mask's pixel positions, so another sprite can be drawn through the mask.
     */
    private static List<BlockElement> maskedElements(int tintIndex, TextureAtlasSprite mask, float offset) {
        List<BlockElement> elements = new ArrayList<>();
        int width = mask.getWidth();
        int height = mask.getHeight();
        float xScale = 16.0F / width;
        float yScale = 16.0F / height;
        for (int y = 0; y < height; y++) {
            int runStart = -1;
            for (int x = 0; x <= width; x++) {
                boolean opaque = x < width && !mask.isTransparent(0, x, y);
                if (opaque && runStart == -1) {
                    runStart = x;
                } else if (!opaque && runStart != -1) {
                    float u0 = runStart * xScale;
                    float u1 = x * xScale;
                    float v0 = y * yScale;
                    float v1 = (y + 1) * yScale;
                    Map<Direction, BlockElementFace> faces = Map.of(
                          Direction.NORTH, new BlockElementFace(null, tintIndex, "layer", new BlockFaceUV(new float[]{u1, v0, u0, v1}, 0)),
                          Direction.SOUTH, new BlockElementFace(null, tintIndex, "layer", new BlockFaceUV(new float[]{u0, v0, u1, v1}, 0))
                    );
                    elements.add(new BlockElement(new Vector3f(u0, 16 - v1, 7.5F - offset * 16), new Vector3f(u1, 16 - v0, 8.5F + offset * 16), faces, null, true));
                    runStart = -1;
                }
            }
        }
        List<BlockElement> generated = ITEM_MODEL_GENERATOR.processFrames(tintIndex, "layer", mask);
        if (generated.size() > 1) {
            elements.addAll(generated.subList(1, generated.size()));
        }
        return elements;
    }

    private static BlockElement offsetFrontBack(BlockElement element, float offset) {
        Vector3f from = element.from.copy();
        Vector3f to = element.to.copy();
        from.setZ(from.z() - offset * 16);
        to.setZ(to.z() + offset * 16);
        return new BlockElement(from, to, element.faces, element.rotation, element.shade);
    }

    private static void addQuads(QuadListModel.Builder builder, List<BlockElement> elements, TextureAtlasSprite sprite, ModelState state,
          ResourceLocation modelLocation) {
        for (BlockElement element : elements) {
            for (Map.Entry<Direction, BlockElementFace> face : element.faces.entrySet()) {
                BakedQuad quad = FACE_BAKERY.bakeQuad(element.from, element.to, face.getValue(), sprite, face.getKey(), state, element.rotation, element.shade,
                      modelLocation);
                builder.addUnculledFace(quad);
            }
        }
    }

    private static final class ContainedFluidOverrideHandler extends ItemOverrides {

        private final Map<Fluid, BakedModel> cache = new ConcurrentHashMap<>();
        private final ItemOverrides nested;
        private final ModelBakery bakery;
        private final IGeometryBakingContext owner;
        private final DynamicFluidContainerModel parent;

        private ContainedFluidOverrideHandler(ItemOverrides nested, ModelBakery bakery, IGeometryBakingContext owner, DynamicFluidContainerModel parent) {
            this.nested = nested;
            this.bakery = bakery;
            this.owner = owner;
            this.parent = parent;
        }

        @Nullable
        @Override
        public BakedModel resolve(@NotNull BakedModel originalModel, @NotNull ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            BakedModel overridden = nested.resolve(originalModel, stack, level, entity, seed);
            if (overridden != originalModel) {
                return overridden;
            }
            return FluidUtil.getFluidContained(stack).map(fluidStack -> {
                Fluid contained = fluidStack.getFluid();
                if (contained == parent.fluid) {
                    return originalModel;
                }
                return cache.computeIfAbsent(contained, f -> parent.withFluid(f).bake(owner, bakery, Material::sprite, BlockModelRotation.X0_Y0, this,
                      new ResourceLocation("forge", "fluid_container_override")));
            }).orElse(originalModel);
        }
    }

    /**
     * Tints the fluid layer of a fluid container item with the color of the fluid it contains.
     */
    public static class Colors implements ItemColor {

        @Override
        public int getColor(@NotNull ItemStack stack, int tintIndex) {
            if (tintIndex != 1) {
                return 0xFFFFFFFF;
            }
            return FluidUtil.getFluidContained(stack)
                  .map(fluidStack -> IClientFluidTypeExtensions.of(fluidStack.getFluid()).getTintColor(fluidStack))
                  .orElse(0xFFFFFFFF);
        }
    }

    public static final class Loader implements IGeometryLoader<DynamicFluidContainerModel> {

        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public DynamicFluidContainerModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) {
            if (!jsonObject.has("fluid")) {
                throw new JsonParseException("Bucket model requires 'fluid' value.");
            }
            ResourceLocation fluidName = new ResourceLocation(GsonHelper.getAsString(jsonObject, "fluid"));
            Fluid fluid = Registry.FLUID.get(fluidName);
            boolean flip = GsonHelper.getAsBoolean(jsonObject, "flip_gas", false);
            boolean coverIsMask = GsonHelper.getAsBoolean(jsonObject, "cover_is_mask", true);
            return new DynamicFluidContainerModel(fluid, flip, coverIsMask);
        }
    }
}
