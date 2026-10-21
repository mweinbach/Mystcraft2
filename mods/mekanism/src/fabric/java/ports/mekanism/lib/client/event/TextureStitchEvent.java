/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import java.util.Set;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import ports.mekanism.lib.eventbus.api.Event;

public class TextureStitchEvent extends Event {

    private final TextureAtlas atlas;

    public TextureStitchEvent(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    public TextureAtlas getAtlas() {
        return atlas;
    }

    /**
     * Fired on the mod buses after an atlas collected the sprites its models reference and before it loads them.
     */
    public static class Pre extends TextureStitchEvent {

        private final Set<ResourceLocation> sprites;

        public Pre(TextureAtlas atlas, Set<ResourceLocation> sprites) {
            super(atlas);
            this.sprites = sprites;
        }

        public boolean addSprite(ResourceLocation sprite) {
            return sprites.add(sprite);
        }
    }

    /**
     * Fired on the mod buses after an atlas uploaded its stitched sprites.
     */
    public static class Post extends TextureStitchEvent {

        public Post(TextureAtlas atlas) {
            super(atlas);
        }
    }
}
