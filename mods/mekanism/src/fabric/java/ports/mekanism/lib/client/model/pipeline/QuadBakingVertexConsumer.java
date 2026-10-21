/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model.pipeline;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Builds {@link BakedQuad}s from vertices written in vanilla's block vertex format, four vertices per quad.
 */
public class QuadBakingVertexConsumer implements VertexConsumer {

    private static final int STRIDE = DefaultVertexFormat.BLOCK.getIntegerSize();
    private static final int POSITION = 0;
    private static final int COLOR = 3;
    private static final int UV0 = 4;
    private static final int UV2 = 6;
    private static final int NORMAL = 7;

    private final Consumer<BakedQuad> quadConsumer;
    private int[] quadData = new int[STRIDE * 4];
    private int vertexIndex;
    private int tintIndex = -1;
    private Direction direction = Direction.DOWN;
    private TextureAtlasSprite sprite;
    private boolean shade = true;
    private boolean hasAmbientOcclusion = true;

    public QuadBakingVertexConsumer(Consumer<BakedQuad> quadConsumer) {
        this.quadConsumer = quadConsumer;
    }

    private int offset(int element) {
        return vertexIndex * STRIDE + element;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        quadData[offset(POSITION)] = Float.floatToRawIntBits((float) x);
        quadData[offset(POSITION) + 1] = Float.floatToRawIntBits((float) y);
        quadData[offset(POSITION) + 2] = Float.floatToRawIntBits((float) z);
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        quadData[offset(COLOR)] = (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        quadData[offset(UV0)] = Float.floatToRawIntBits(u);
        quadData[offset(UV0) + 1] = Float.floatToRawIntBits(v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        quadData[offset(UV2)] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        quadData[offset(NORMAL)] = ((int) (Mth.clamp(x, -1, 1) * 127) & 0xFF) | ((int) (Mth.clamp(y, -1, 1) * 127) & 0xFF) << 8
                                   | ((int) (Mth.clamp(z, -1, 1) * 127) & 0xFF) << 16;
        return this;
    }

    @Override
    public void endVertex() {
        if (++vertexIndex == 4) {
            quadConsumer.accept(new BakedQuad(quadData, tintIndex, direction, sprite, shade));
            vertexIndex = 0;
            quadData = new int[STRIDE * 4];
        }
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
    }

    @Override
    public void unsetDefaultColor() {
    }

    public void setTintIndex(int tintIndex) {
        this.tintIndex = tintIndex;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public void setSprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    public void setShade(boolean shade) {
        this.shade = shade;
    }

    /**
     * Kept for API compatibility; vanilla quads on Fabric have no per-quad ambient occlusion flag.
     */
    public void setHasAmbientOcclusion(boolean hasAmbientOcclusion) {
        this.hasAmbientOcclusion = hasAmbientOcclusion;
    }

    public static class Buffered extends QuadBakingVertexConsumer {

        private final BakedQuad[] output;

        public Buffered() {
            this(new BakedQuad[1]);
        }

        private Buffered(BakedQuad[] output) {
            super(quad -> output[0] = quad);
            this.output = output;
        }

        public BakedQuad getQuad() {
            BakedQuad quad = output[0];
            if (quad == null) {
                throw new IllegalStateException("No quad has been emitted. Vertices in buffer: " + super.vertexIndex);
            }
            output[0] = null;
            return quad;
        }
    }
}
