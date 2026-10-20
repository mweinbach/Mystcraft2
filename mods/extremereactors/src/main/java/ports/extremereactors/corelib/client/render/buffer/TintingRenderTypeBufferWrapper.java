/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.render.buffer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * Wraps a buffer source so that every vertex written through it has its colour multiplied by a fixed tint.
 */
public class TintingRenderTypeBufferWrapper
        implements MultiBufferSource {

    public TintingRenderTypeBufferWrapper(final MultiBufferSource source, final float alpha, final float red,
                                          final float green, final float blue) {

        this._source = source;
        this._alpha = alpha;
        this._red = red;
        this._green = green;
        this._blue = blue;
    }

    @Override
    public VertexConsumer getBuffer(final RenderType type) {
        return new Tinting(this._source.getBuffer(type));
    }

    private final class Tinting
            implements VertexConsumer {

        Tinting(final VertexConsumer wrapped) {
            this._wrapped = wrapped;
        }

        @Override
        public VertexConsumer vertex(final double x, final double y, final double z) {

            this._wrapped.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(final int red, final int green, final int blue, final int alpha) {

            this._wrapped.color(scale(red, _red), scale(green, _green), scale(blue, _blue), scale(alpha, _alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(final float u, final float v) {

            this._wrapped.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(final int u, final int v) {

            this._wrapped.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(final int u, final int v) {

            this._wrapped.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(final float x, final float y, final float z) {

            this._wrapped.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            this._wrapped.endVertex();
        }

        @Override
        public void defaultColor(final int red, final int green, final int blue, final int alpha) {
            this._wrapped.defaultColor(scale(red, _red), scale(green, _green), scale(blue, _blue), scale(alpha, _alpha));
        }

        @Override
        public void unsetDefaultColor() {
            this._wrapped.unsetDefaultColor();
        }

        private static int scale(final int component, final float factor) {
            return Math.max(0, Math.min(255, (int) (component * factor)));
        }

        private final VertexConsumer _wrapped;
    }

    private final MultiBufferSource _source;
    private final float _alpha;
    private final float _red;
    private final float _green;
    private final float _blue;
}
