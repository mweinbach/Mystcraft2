/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Untextured, unlit quads blended additively and drawn without writing depth, like vanilla lightning: glow that
 * brightens what is behind it and shows through glass.
 */
final class GlowRenderType
        extends RenderType {

    static final RenderType GLOW = new GlowRenderType();

    private GlowRenderType() {
        super("bigreactors_effects_glow", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096,
                false, true,
                () -> {
                    RENDERTYPE_LIGHTNING_SHADER.setupRenderState();
                    LIGHTNING_TRANSPARENCY.setupRenderState();
                    LEQUAL_DEPTH_TEST.setupRenderState();
                    COLOR_WRITE.setupRenderState();
                    NO_CULL.setupRenderState();
                },
                () -> {
                    NO_CULL.clearRenderState();
                    LEQUAL_DEPTH_TEST.clearRenderState();
                    COLOR_WRITE.clearRenderState();
                    LIGHTNING_TRANSPARENCY.clearRenderState();
                    RENDERTYPE_LIGHTNING_SHADER.clearRenderState();
                });
    }
}
