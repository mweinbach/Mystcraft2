/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import ports.mekanism.lib.client.model.geometry.IGeometryBakingContext;
import ports.mekanism.lib.client.model.geometry.IGeometryLoader;
import ports.mekanism.lib.client.model.geometry.IUnbakedGeometry;

/**
 * Geometry made of vanilla block model elements, baked with the declaring model's textures and root transform.
 */
public class ElementsModel implements IUnbakedGeometry<ElementsModel> {

    private static final FaceBakery FACE_BAKERY = new FaceBakery();

    private final List<BlockElement> elements;

    public ElementsModel(List<BlockElement> elements) {
        this.elements = elements;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
          ItemOverrides overrides, ResourceLocation modelLocation) {
        TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
        QuadListModel.Builder builder = QuadListModel.builder(context, particle, overrides, context.getTransforms());
        ModelState state = UnbakedGeometryHelper.composeRootTransform(modelState, context.getRootTransform());
        for (BlockElement element : elements) {
            for (Direction direction : element.faces.keySet()) {
                BlockElementFace face = element.faces.get(direction);
                TextureAtlasSprite sprite = spriteGetter.apply(context.getMaterial(face.texture));
                var quad = FACE_BAKERY.bakeQuad(element.from, element.to, face, sprite, direction, state, element.rotation, element.shade, modelLocation);
                if (face.cullForDirection == null) {
                    builder.addUnculledFace(quad);
                } else {
                    builder.addCulledFace(Direction.rotate(state.getRotation().getMatrix(), face.cullForDirection), quad);
                }
            }
        }
        return builder.build();
    }

    @Override
    public Collection<Material> getMaterials(IGeometryBakingContext context, Function<ResourceLocation, UnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors) {
        Set<Material> textures = new HashSet<>();
        if (context.hasMaterial("particle")) {
            textures.add(context.getMaterial("particle"));
        }
        for (BlockElement element : elements) {
            for (BlockElementFace face : element.faces.values()) {
                Material texture = context.getMaterial(face.texture);
                if (texture.texture().equals(MissingTextureAtlasSprite.getLocation())) {
                    missingTextureErrors.add(Pair.of(face.texture, context.getModelName()));
                }
                textures.add(texture);
            }
        }
        return textures;
    }

    public static final class Loader implements IGeometryLoader<ElementsModel> {

        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public ElementsModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
            if (!jsonObject.has("elements")) {
                throw new JsonParseException("An element model must have an \"elements\" member.");
            }
            List<BlockElement> elements = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(jsonObject, "elements")) {
                elements.add(deserializationContext.deserialize(element, BlockElement.class));
            }
            return new ElementsModel(elements);
        }
    }
}
