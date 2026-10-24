/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model.geometry;

import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.client.NamedRenderTypeManager;
import ports.projecte.lib.client.RenderTypeGroup;

/**
 * What a geometry needs from the model that declared it: its textures, transforms, lighting and visible parts.
 */
public interface IGeometryBakingContext {

    String getModelName();

    boolean hasMaterial(String name);

    Material getMaterial(String name);

    boolean isGui3d();

    boolean useBlockLight();

    boolean useAmbientOcclusion();

    ItemTransforms getTransforms();

    Transformation getRootTransform();

    @Nullable
    ResourceLocation getRenderTypeHint();

    @Nullable
    default ResourceLocation getRenderTypeFastHint() {
        return null;
    }

    boolean isComponentVisible(String component, boolean fallback);

    default RenderTypeGroup getRenderType(ResourceLocation name) {
        return NamedRenderTypeManager.get(name);
    }
}
