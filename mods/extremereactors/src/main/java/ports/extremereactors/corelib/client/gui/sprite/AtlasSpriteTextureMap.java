/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.sprite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * A texture atlas that sprites can be looked up in.
 */
public final class AtlasSpriteTextureMap {

    public static final AtlasSpriteTextureMap BLOCKS = new AtlasSpriteTextureMap(InventoryMenu.BLOCK_ATLAS);

    public ResourceLocation getAtlasId() {
        return this._atlasId;
    }

    public TextureAtlas getAtlas() {
        return Minecraft.getInstance().getModelManager().getAtlas(this._atlasId);
    }

    private AtlasSpriteTextureMap(final ResourceLocation atlasId) {
        this._atlasId = atlasId;
    }

    private final ResourceLocation _atlasId;
}
