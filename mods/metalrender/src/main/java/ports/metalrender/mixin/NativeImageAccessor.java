/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native pixel pointer of a NativeImage, handed straight to Metal without copying. */
@Mixin(NativeImage.class)
public interface NativeImageAccessor {

    @Accessor("pixels")
    long metalrender$pixels();
}
