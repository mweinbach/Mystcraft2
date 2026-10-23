/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import ports.metalrender.nativebridge.MetalNative;

/**
 * Draws a Metal IOSurface pair (BGRA colour + R32F window depth) into the currently bound OpenGL
 * framebuffer with one full-screen triangle, writing {@code gl_FragDepth} so OpenGL entities, block
 * entities, translucent terrain and particles depth-test against Metal terrain. All OpenGL state it
 * touches is restored.
 *
 * <p>Scaled frames also get a render-resolution copy of the OpenGL frame (the sky) via
 * {@link #copyBackground}, which Metal uses to fill pixels without terrain before upscaling. The
 * upscaled silhouette then blends into the real sky, and the compositor writes those blended pixels
 * up to {@code edge} pixels outside the (nearest-upscaled) terrain coverage instead of cutting the
 * silhouette along half-resolution steps.
 */
final class GlCompositor {

    private static final int SLOTS = 3;

    private static final String VERTEX = """
            #version 150
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    // IOSurface row 0 is the top of the image; OpenGL window row 0 is the bottom. Empty pixels keep
    // the cleared depth of 1.0; MetalFX output has opaque alpha everywhere, so depth decides coverage.
    private static final String FRAGMENT = """
            #version 150
            uniform sampler2DRect uColor;
            uniform sampler2DRect uDepth;
            uniform float uHeight;
            uniform int uEdge;
            out vec4 fragColor;
            void main() {
                ivec2 last = textureSize(uDepth) - 1;
                ivec2 p = clamp(ivec2(int(gl_FragCoord.x), int(uHeight - gl_FragCoord.y)), ivec2(0), last);
                vec4 c = texelFetch(uColor, p);
                float depth = texelFetch(uDepth, p).r;
                if (depth >= 1.0) {
                    // Empty pixel: keep the upscaled sky/terrain blend only next to terrain.
                    for (int dy = -uEdge; dy <= uEdge; dy++) {
                        for (int dx = -uEdge; dx <= uEdge; dx++) {
                            if (texelFetch(uDepth, clamp(p + ivec2(dx, dy), ivec2(0), last)).r < 1.0) {
                                fragColor = vec4(c.rgb, 1.0);
                                gl_FragDepth = 1.0;
                                return;
                            }
                        }
                    }
                    discard;
                }
                if (c.a <= 0.0) {
                    discard;
                }
                fragColor = c;
                gl_FragDepth = depth;
            }
            """;

    private final int program;
    private final int vao;
    private final int heightUniform;
    private final int edgeUniform;
    /** [slot][colour, depth]. */
    private final int[][] textures = new int[SLOTS][2];
    private final int backgroundTexture;
    private final int backgroundFbo;

    private GlCompositor(final int program) {
        this.program = program;
        this.vao = GL30.glGenVertexArrays();
        this.heightUniform = GL20.glGetUniformLocation(program, "uHeight");
        this.edgeUniform = GL20.glGetUniformLocation(program, "uEdge");
        for (final int[] slot : textures) {
            for (int i = 0; i < slot.length; i++) {
                slot[i] = GL11.glGenTextures();
            }
        }
        this.backgroundTexture = GL11.glGenTextures();
        this.backgroundFbo = GL30.glGenFramebuffers();
        final int previous = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
        GlStateManager._glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uColor"), 0);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uDepth"), 1);
        GlStateManager._glUseProgram(previous);
    }

    static GlCompositor create() {
        final int vs = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        final int fs = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        final int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL30.glBindFragDataLocation(program, 0, "fragColor");
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            final String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            throw new IllegalStateException("compositor link failed: " + log);
        }
        return new GlCompositor(program);
    }

    private static int compile(final int type, final String source) {
        final int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            final String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("compositor shader compile failed: " + log);
        }
        return shader;
    }

    /**
     * Copies {@code source}'s colour, scaled to {@code width}x{@code height}, into the background
     * IOSurface for the next opaque pass. Call before {@link MetalNative#renderOpaquePass}.
     */
    void copyBackground(final long ctx, final RenderTarget source, final int width, final int height) {
        if (!MetalNative.prepareBackground(ctx, backgroundTexture, width, height)) {
            return; // the opaque pass then simply skips the fill
        }
        final int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        if (scissor) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, backgroundFbo);
        // Re-attach every time: prepareBackground may have replaced the texture's IOSurface storage.
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL31.GL_TEXTURE_RECTANGLE,
                backgroundTexture, 0);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        // Flip vertically: IOSurface row 0 is the top of the image.
        GL30.glBlitFramebuffer(0, 0, source.width, source.height, 0, height, width, 0,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        if (scissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glFlush(); // Metal reads the IOSurface next
    }

    /**
     * Replaces colour and depth wherever terrain was drawn.
     *
     * @param edge pixels outside terrain coverage that still take the composited colour (0 = none);
     *             only meaningful when the frame was filled from {@link #copyBackground}
     * @return false if the IOSurfaces could not be attached
     */
    boolean composite(final long ctx, final int slot, final int height, final int edge) {
        final int color = textures[slot][0];
        final int depth = textures[slot][1];
        final int previousProgram = GlStateManager._getInteger(GL20.GL_CURRENT_PROGRAM);
        final int previousVao = GlStateManager._getInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int previousUnit = GlStateManager._getActiveTexture();

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        if (!MetalNative.bindIOSurfaceToGLTexture(ctx, slot, false, color, depth)) {
            GlStateManager._activeTexture(previousUnit);
            return false;
        }
        GL11.glBindTexture(GL31.GL_TEXTURE_RECTANGLE, color);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL31.GL_TEXTURE_RECTANGLE, depth);

        GlStateManager._glUseProgram(program);
        GL20.glUniform1f(heightUniform, height);
        GL20.glUniform1i(edgeUniform, edge);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        GlStateManager._glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        GlStateManager._glBindVertexArray(previousVao);
        GL11.glBindTexture(GL31.GL_TEXTURE_RECTANGLE, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL31.GL_TEXTURE_RECTANGLE, 0);
        GlStateManager._activeTexture(previousUnit);
        GlStateManager._glUseProgram(previousProgram);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        return true;
    }

    void close() {
        for (final int[] slot : textures) {
            for (final int texture : slot) {
                GL11.glDeleteTextures(texture);
            }
        }
        GL11.glDeleteTextures(backgroundTexture);
        GL30.glDeleteFramebuffers(backgroundFbo);
        GL30.glDeleteVertexArrays(vao);
        GL20.glDeleteProgram(program);
    }
}
