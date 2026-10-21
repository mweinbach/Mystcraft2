/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model.geometry;

import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * Geometry read by an {@link IGeometryLoader}, baked with the context of the model that declared it.
 */
public interface IUnbakedGeometry<T extends IUnbakedGeometry<T>> {

    BakedModel bake(IGeometryBakingContext context, ModelBakery bakery, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
          ItemOverrides overrides, ResourceLocation modelLocation);

    Collection<Material> getMaterials(IGeometryBakingContext context, Function<ResourceLocation, UnbakedModel> modelGetter, Set<Pair<String, String>> missingTextureErrors);

    default Set<String> getConfigurableComponentNames() {
        return Collections.emptySet();
    }
}
