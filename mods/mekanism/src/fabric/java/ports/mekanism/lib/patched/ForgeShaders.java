/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.patched;

import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * Forge's {@code ShaderInstance(ResourceProvider, ResourceLocation, VertexFormat)}. A mixin on {@link ShaderInstance}
 * lets the namespaced name find {@code <namespace>:shaders/core/<path>.json} and its programs, as Forge's does.
 */
public final class ForgeShaders {

    private ForgeShaders() {
    }

    public static ShaderInstance create(ResourceProvider resources, ResourceLocation name, VertexFormat format) throws IOException {
        return new ShaderInstance(resources, name.toString(), format);
    }
}
