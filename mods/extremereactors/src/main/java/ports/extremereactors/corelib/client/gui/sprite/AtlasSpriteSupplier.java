/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.sprite;

import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.CodeHelper;
import ports.extremereactors.platform.ClientPlatform;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Supplies a sprite from a texture atlas, looked up again after each resource reload.
 */
public final class AtlasSpriteSupplier
        implements Supplier<ISprite> {

    public static Supplier<ISprite> create(final ResourceLocation spriteId, final AtlasSpriteTextureMap atlas) {
        return create(spriteId, atlas, false);
    }

    /**
     * @param stitch true to add the sprite to the atlas, for textures that no model references
     */
    public static Supplier<ISprite> create(final ResourceLocation spriteId, final AtlasSpriteTextureMap atlas,
                                           final boolean stitch) {

        if (stitch) {
            ClientPlatform.backend().addAtlasSprite(atlas.getAtlasId(), spriteId);
        }

        return new AtlasSpriteSupplier(spriteId, atlas);
    }

    @Override
    public ISprite get() {

        ISprite sprite = this._sprite;

        if (null == sprite) {
            this._sprite = sprite = new AtlasSprite(this._atlas.getAtlas().getSprite(this._spriteId));
        }

        return sprite;
    }

    private record AtlasSprite(TextureAtlasSprite sprite) implements ISprite {

        @Override
        public Optional<TextureAtlasSprite> getAtlasSprite() {
            return MissingTextureAtlasSprite.getLocation().equals(this.sprite.getName()) ?
                    Optional.empty() : Optional.of(this.sprite);
        }
    }

    private AtlasSpriteSupplier(final ResourceLocation spriteId, final AtlasSpriteTextureMap atlas) {

        this._spriteId = spriteId;
        this._atlas = atlas;
        CodeHelper.addResourceReloadListener(() -> this._sprite = null);
    }

    private final ResourceLocation _spriteId;
    private final AtlasSpriteTextureMap _atlas;
    private volatile ISprite _sprite;
}
