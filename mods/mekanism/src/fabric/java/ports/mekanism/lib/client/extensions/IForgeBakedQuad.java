/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.extensions;

/**
 * Forge's per-quad ambient occlusion flag, injected into {@link net.minecraft.client.renderer.block.model.BakedQuad}
 * on Fabric. Vanilla quads are always occluded by the model's own setting.
 */
public interface IForgeBakedQuad {

    default boolean hasAmbientOcclusion() {
        return true;
    }
}
