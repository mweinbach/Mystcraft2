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

import javax.annotation.Nullable;
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
            this._sprite = sprite = of(this._atlas.getAtlas().getSprite(this._spriteId));
        }

        return sprite;
    }

    /** A sprite for an entry of a stitched texture atlas. */
    public static ISprite of(final TextureAtlasSprite sprite) {
        return new AtlasSprite(sprite, null);
    }

    private record AtlasSprite(TextureAtlasSprite sprite, @Nullable ISprite overlay) implements ISprite {

        @Override
        public Optional<TextureAtlasSprite> getAtlasSprite() {
            return MissingTextureAtlasSprite.getLocation().equals(this.sprite.getName()) ?
                    Optional.empty() : Optional.of(this.sprite);
        }

        @Override
        public ResourceLocation getTextureLocation() {
            return this.sprite.atlas().location();
        }

        @Override
        public int getWidth() {
            return this.sprite.getWidth();
        }

        @Override
        public int getHeight() {
            return this.sprite.getHeight();
        }

        @Override
        public float getMinU() {
            return this.sprite.getU0();
        }

        @Override
        public float getMaxU() {
            return this.sprite.getU1();
        }

        @Override
        public float getMinV() {
            return this.sprite.getV0();
        }

        @Override
        public float getMaxV() {
            return this.sprite.getV1();
        }

        @Override
        public Optional<ISprite> getSpriteOverlay() {
            return Optional.ofNullable(this.overlay);
        }

        @Override
        public ISprite copyWith(final ISprite overlay) {
            return new AtlasSprite(this.sprite, overlay);
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
