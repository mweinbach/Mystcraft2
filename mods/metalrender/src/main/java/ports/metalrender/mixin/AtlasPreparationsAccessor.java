/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import java.util.List;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TextureAtlas.Preparations.class)
public interface AtlasPreparationsAccessor {

    @Accessor("width")
    int metalrender$width();

    @Accessor("height")
    int metalrender$height();

    @Accessor("regions")
    List<TextureAtlasSprite> metalrender$regions();
}
