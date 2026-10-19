package net.mystcraft.minecraft.client.age;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.mystcraft.minecraft.age.visual.AgeCelestialSettings;
import net.mystcraft.minecraft.age.visual.AgeVisualSettings;
import net.mystcraft.minecraft.age.visual.CelestialMath;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.material.FogType;

/** Shared modern renderer for the realized, ordered classic celestial list. */
public final class AgeSkyRenderer {
    private static final ResourceLocation SUN = new ResourceLocation("minecraft", "textures/environment/sun.png");
    private static final ResourceLocation MOON = new ResourceLocation("minecraft", "textures/environment/moon_phases.png");
    private static final ResourceLocation END = new ResourceLocation("minecraft", "textures/environment/end_sky.png");
    private static VertexBuffer normalStars;
    private static VertexBuffer rainbow;
    private record Layers(long[] offsets, VertexBuffer[] buffers) {}
    private static final Map<Long, Layers> TWINKLE = new LinkedHashMap<>(16, .75F, true);
    private static boolean reloadRegistered;
    private AgeSkyRenderer() {}

    private static void initialize() {
        if (reloadRegistered) return;
        if (Minecraft.getInstance().getResourceManager() instanceof ReloadableResourceManager resources) {
            resources.registerReloadListener((ResourceManagerReloadListener) ignored -> clear());
            reloadRegistered = true;
        }
    }
    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(AgeSkyRenderer::clear); return; }
        if (rainbow != null) { rainbow.close(); rainbow = null; }
        if (normalStars != null) { normalStars.close(); normalStars = null; }
        for (Layers layers : TWINKLE.values()) for (VertexBuffer buffer : layers.buffers()) buffer.close();
        TWINKLE.clear();
    }
    /** Returns false only when the native vanilla sky should remain in charge. */
    public static boolean render(ClientLevel level, PoseStack pose, Matrix4f projection, float partial, Camera camera, boolean foggy, Runnable setupFog) {
        AgeCelestialSettings settings = AgeVisualClient.settings(level).celestials();
        if (settings.legacyVanilla()) return false;
        if (foggy || camera.getFluidInCamera() == FogType.LAVA || camera.getFluidInCamera() == FogType.POWDER_SNOW) return true;
        initialize();
        setupFog.run();
        RenderSystem.depthMask(false); RenderSystem.disableDepthTest(); RenderSystem.disableCull();
        pose.pushPose();
        try {
            var sky = level.getSkyColor(camera.getPosition(), partial);
            foggedSkyDisk(pose, sky);
            RenderSystem.enableBlend();
            float rain = 1 - level.getRainLevel(partial);
            for (var body : settings.bodies()) {
                if (body.kind() == AgeCelestialSettings.Kind.RAINBOW) { rainbow(pose, projection, body); continue; }
                if (body.kind() == AgeCelestialSettings.Kind.END_SKY) { endSky(level, pose, body, partial); continue; }
                pose.pushPose();
                pose.mulPose(Vector3f.YP.rotationDegrees(body.yaw()));
                pose.mulPose(Vector3f.XP.rotationDegrees(CelestialMath.altitude(body, level.getDayTime(), partial) * 360));
                if (body.kind() == AgeCelestialSettings.Kind.SUN || body.kind() == AgeCelestialSettings.Kind.MOON)
                    orbit(level, pose, body, partial, rain);
                else stars(level, pose, projection, body, partial, rain);
                pose.popPose();
                if (body.kind() == AgeCelestialSettings.Kind.SUN || body.kind() == AgeCelestialSettings.Kind.MOON)
                    sunset(level, pose, settings, body, partial);
            }
            if (!settings.endSky() && AgeVisualClient.settings(level).environment().drawVoid()) {
                // SkyRendererMyst draws the black below-horizon enclosure first, then the
                // sky-colored lower plane. Additive celestial state must not leak here.
                RenderSystem.disableBlend();
                float horizonDistance = (float) (camera.getPosition().y - AgeVisualClient.settings(level).environment().horizon());
                if (horizonDistance < 0) {
                    disk(pose, -4, 0, 0, 0); // source lower list at -16 translated upward by 12
                    voidEnclosure(pose, -(horizonDistance + 65));
                }
                disk(pose, -horizonDistance, (float) sky.x, (float) sky.y, (float) sky.z);
            }
        } finally {
            pose.popPose(); VertexBuffer.unbind();
            RenderSystem.setShaderColor(1, 1, 1, 1); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
            RenderSystem.enableCull(); RenderSystem.enableDepthTest(); RenderSystem.depthMask(true); setupFog.run();
        }
        return true;
    }
    /** Classic glSkyList was drawn with GL_FOG enabled. PositionColor's modern shader has no fog,
     * so interpolate its radial rings into the current native fog color explicitly. */
    private static void foggedSkyDisk(PoseStack pose, net.minecraft.world.phys.Vec3 sky) {
        RenderSystem.disableBlend(); RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder(); Matrix4f matrix = pose.last().pose();
        float[] fog = RenderSystem.getShaderFogColor();
        float start = RenderSystem.getShaderFogStart(), end = RenderSystem.getShaderFogEnd();
        float[] radii = {0, 16, 32, 64, 128, 256, 512};
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int ring = 0; ring < radii.length - 1; ring++) {
            float inner = radii[ring], outer = radii[ring + 1];
            for (int sector = 0; sector < 32; sector++) {
                double a = sector * Math.PI * 2 / 32, b = (sector + 1) * Math.PI * 2 / 32;
                skyVertex(buffer, matrix, inner, a, sky, fog, start, end);
                skyVertex(buffer, matrix, outer, a, sky, fog, start, end);
                skyVertex(buffer, matrix, outer, b, sky, fog, start, end);
                skyVertex(buffer, matrix, inner, b, sky, fog, start, end);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }
    private static void skyVertex(BufferBuilder buffer, Matrix4f matrix, float radius, double angle,
                                  net.minecraft.world.phys.Vec3 sky, float[] fog, float start, float end) {
        float blend = end > start ? Math.max(0, Math.min(1, (radius - start) / (end - start))) : 0;
        buffer.vertex(matrix, (float) Math.cos(angle) * radius, 16, (float) Math.sin(angle) * radius)
                .color((float) sky.x * (1 - blend) + fog[0] * blend, (float) sky.y * (1 - blend) + fog[1] * blend,
                        (float) sky.z * (1 - blend) + fog[2] * blend, 1).endVertex();
    }
    private static void disk(PoseStack pose, float y, float red, float green, float blue) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder(); Matrix4f matrix = pose.last().pose();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0, y, 0).color(red, green, blue, 1).endVertex();
        for (int i = 0; i <= 16; i++) {
            double angle = i * Math.PI * 2 / 16;
            buffer.vertex(matrix, (float)Math.cos(angle) * 512, y, (float)Math.sin(angle) * 512).color(red, green, blue, 1).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
    }
    /** Source SkyRendererMyst's four black walls and lid below the horizon. */
    private static void voidEnclosure(PoseStack pose, float bottom) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        Matrix4f matrix = pose.last().pose();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float[][] corners = {{-1, 1}, {1, 1}, {1, -1}, {-1, -1}};
        for (int side = 0; side < 4; side++) {
            float[] a = corners[side], b = corners[(side + 1) % 4];
            blackVertex(buffer, matrix, a[0], bottom, a[1]);
            blackVertex(buffer, matrix, b[0], bottom, b[1]);
            blackVertex(buffer, matrix, b[0], -1, b[1]);
            blackVertex(buffer, matrix, a[0], -1, a[1]);
        }
        for (float[] corner : corners) blackVertex(buffer, matrix, corner[0], -1, corner[1]);
        BufferUploader.drawWithShader(buffer.end());
    }
    private static void blackVertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z) {
        buffer.vertex(matrix, x, y, z).color(0, 0, 0, 1).endVertex();
    }
    private static void rainbow(PoseStack pose, Matrix4f projection, AgeCelestialSettings.Body body) {
        if (rainbow == null) rainbow = rainbowGeometry();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        pose.pushPose(); pose.mulPose(Vector3f.YP.rotationDegrees(body.yaw())); pose.mulPose(Vector3f.XP.rotationDegrees(45));
        for (int half = 0; half < 2; half++) {
            rainbow.bind(); rainbow.drawWithShader(pose.last().pose(), projection, GameRenderer.getPositionColorShader());
            pose.mulPose(Vector3f.XP.rotationDegrees(180));
        }
        pose.popPose(); VertexBuffer.unbind();
    }
    private static VertexBuffer rainbowGeometry() {
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int band = 0; band < 50; band++) {
            int color = net.mystcraft.minecraft.age.visual.RainbowMath.color(band);
            float red = (color >> 16 & 255) / 255F, green = (color >> 8 & 255) / 255F, blue = (color & 255) / 255F;
            // Upstream's first patch requests sqrt(1 - 1.005^2), producing NaN. Clip that invalid patch.
            for (int patch = -199; patch <= 200; patch++) {
                float z = patch * .5F, y = net.mystcraft.minecraft.age.visual.RainbowMath.height(z), previous = net.mystcraft.minecraft.age.visual.RainbowMath.height(z - .5F);
                buffer.vertex(band - 26, previous, z).color(red, green, blue, .2F).endVertex();
                buffer.vertex(band - 24, previous, z).color(red, green, blue, .2F).endVertex();
                buffer.vertex(band - 24, y, z + .5F).color(red, green, blue, .2F).endVertex();
                buffer.vertex(band - 26, y, z + .5F).color(red, green, blue, .2F).endVertex();
            }
        }
        VertexBuffer result = new VertexBuffer(); result.bind(); result.upload(buffer.end()); VertexBuffer.unbind(); return result;
    }
    private static void additive() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }
    private static void orbit(ClientLevel level, PoseStack pose, AgeCelestialSettings.Body body, float partial, float rain) {
        additive(); RenderSystem.setShader(GameRenderer::getPositionTexShader); RenderSystem.setShaderColor(1, 1, 1, rain);
        boolean sun = body.kind() == AgeCelestialSettings.Kind.SUN;
        RenderSystem.setShaderTexture(0, sun ? SUN : MOON);
        float size = sun ? 30 : 20, u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (!sun) {
            int phase = CelestialMath.moonPhase(body, level.getDayTime());
            u0 = phase % 4 / 4F; u1 = (phase % 4 + 1) / 4F; v0 = phase / 4 % 2 / 2F; v1 = (phase / 4 % 2 + 1) / 2F;
        }
        BufferBuilder buffer = Tesselator.getInstance().getBuilder(); Matrix4f matrix = pose.last().pose();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, -size, 100, -size).uv(sun ? u0 : u1, sun ? v0 : v1).endVertex();
        buffer.vertex(matrix, size, 100, -size).uv(sun ? u1 : u0, sun ? v0 : v1).endVertex();
        buffer.vertex(matrix, size, 100, size).uv(sun ? u1 : u0, sun ? v1 : v0).endVertex();
        buffer.vertex(matrix, -size, 100, size).uv(sun ? u0 : u1, sun ? v1 : v0).endVertex();
        BufferUploader.drawWithShader(buffer.end()); RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private static AgeVisualSettings.Rgb color(List<AgeVisualSettings.Stop> gradient, long time, float partial) {
        return new AgeVisualSettings.Provider("gradient", gradient).color((time + partial) / 12000D);
    }
    private static void stars(ClientLevel level, PoseStack pose, Matrix4f projection, AgeCelestialSettings.Body body, float partial, float rain) {
        float brightness = level.getStarBrightness(partial) * rain;
        if (brightness <= 0) return;
        additive(); var tint = color(body.gradient(), level.getDayTime(), partial);
        RenderSystem.setShader(GameRenderer::getPositionShader);
        if (body.kind() == AgeCelestialSettings.Kind.STARS) {
            if (normalStars == null) normalStars = createStars(new Random(10842), 1500);
            drawStars(normalStars, pose, projection, tint, brightness);
        } else {
            Layers layers = TWINKLE.get(body.renderSeed());
            if (layers == null) {
                Random random = new Random(body.renderSeed()); long[] offsets = new long[10]; VertexBuffer[] buffers = new VertexBuffer[10];
                for (int i = 0; i < 10; i++) offsets[i] = random.nextLong();
                for (int i = 0; i < 10; i++) buffers[i] = createStars(random, 100);
                layers = new Layers(offsets, buffers); TWINKLE.put(body.renderSeed(), layers);
                if (TWINKLE.size() > 256) {
                    var oldest = TWINKLE.entrySet().iterator(); Layers expired = oldest.next().getValue(); oldest.remove();
                    for (VertexBuffer buffer : expired.buffers()) buffer.close();
                }
            }
            for (int i = 0; i < 10; i++) drawStars(layers.buffers()[i], pose, projection, tint,
                    brightness * CelestialMath.twinkle(level.getDayTime(), partial, layers.offsets()[i]));
        }
        VertexBuffer.unbind(); RenderSystem.setShaderColor(1, 1, 1, 1);
    }
    private static void drawStars(VertexBuffer stars, PoseStack pose, Matrix4f projection, AgeVisualSettings.Rgb color, float alpha) {
        RenderSystem.setShaderColor(color.red(), color.green(), color.blue(), alpha);
        stars.bind(); stars.drawWithShader(pose.last().pose(), projection, GameRenderer.getPositionShader());
    }
    /** Exact 10842 normal-star sphere samples and the same basis for seeded twinkle layers. */
    private static VertexBuffer createStars(Random random, int samples) {
        BufferBuilder buffer = new BufferBuilder(samples * 48);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int i = 0; i < samples; i++) {
            double x = random.nextFloat() * 2F - 1, y = random.nextFloat() * 2F - 1, z = random.nextFloat() * 2F - 1;
            double size = .15F + random.nextFloat() * .1F, square = x*x + y*y + z*z;
            if (square >= 1 || square <= .01) continue;
            double inverse = 1 / Math.sqrt(square); x *= inverse; y *= inverse; z *= inverse;
            double azimuth = Math.atan2(x, z), sinA = Math.sin(azimuth), cosA = Math.cos(azimuth);
            double polar = Math.atan2(Math.sqrt(x*x + z*z), y), sinP = Math.sin(polar), cosP = Math.cos(polar);
            double spin = random.nextDouble() * Math.PI * 2, sinS = Math.sin(spin), cosS = Math.cos(spin);
            for (int j = 0; j < 4; j++) {
                double a = ((j & 2) - 1) * size, b = (((j + 1) & 2) - 1) * size;
                double localX = a * cosS - b * sinS, localZ = b * cosS + a * sinS;
                double dy = localX * sinP, radial = -localX * cosP;
                buffer.vertex(x*100 + radial*sinA - localZ*cosA, y*100 + dy, z*100 + localZ*sinA + radial*cosA).endVertex();
            }
        }
        VertexBuffer result = new VertexBuffer(); result.bind(); result.upload(buffer.end()); VertexBuffer.unbind();
        return result;
    }
    private static void sunset(ClientLevel level, PoseStack pose, AgeCelestialSettings settings, AgeCelestialSettings.Body body, float partial) {
        if (body.kind() == AgeCelestialSettings.Kind.MOON && body.sunset().isEmpty()) return;
        float angle = CelestialMath.altitude(body, level.getDayTime(), partial), alpha = CelestialMath.sunsetAlpha(angle);
        if (alpha == 0) return;
        List<AgeVisualSettings.Stop> gradient = body.sunset().isEmpty() ? settings.sunset() : body.sunset();
        float phase = (float)Math.cos(angle * Math.PI * 2) / .4F * .5F + .5F;
        AgeVisualSettings.Rgb rgb = gradient.isEmpty() ? new AgeVisualSettings.Rgb(phase*.3F+.7F, phase*phase*.7F+.2F, .2F)
                : color(gradient, level.getDayTime(), partial);
        if (body.kind() == AgeCelestialSettings.Kind.MOON) alpha *= .3F;
        // Source Sun/Moon set SRC_ALPHA, ONE before renderHorizon; the fan inherits it.
        additive(); RenderSystem.setShader(GameRenderer::getPositionColorShader); RenderSystem.setShaderColor(1, 1, 1, 1);
        pose.pushPose(); pose.mulPose(Vector3f.XP.rotationDegrees(90));
        pose.mulPose(Vector3f.ZP.rotationDegrees(Math.sin(angle*Math.PI*2) < 0 ? 180 : 0)); pose.mulPose(Vector3f.ZP.rotationDegrees(-body.yaw()));
        Matrix4f matrix = pose.last().pose(); BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(matrix, 0, 100, 0).color(rgb.red(), rgb.green(), rgb.blue(), alpha).endVertex();
        for (int i = 0; i <= 16; i++) {
            float turn = (float)(i * Math.PI * 2 / 16);
            buffer.vertex(matrix, (float)Math.sin(turn)*120, (float)Math.cos(turn)*120, -(float)Math.cos(turn)*40*alpha).color(rgb.red(), rgb.green(), rgb.blue(), 0).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end()); pose.popPose();
    }
    private static void endSky(ClientLevel level, PoseStack pose, AgeCelestialSettings.Body body, float partial) {
        var tint = color(body.gradient(), level.getDayTime(), partial);
        RenderSystem.defaultBlendFunc(); RenderSystem.setShader(GameRenderer::getPositionTexColorShader); RenderSystem.setShaderTexture(0, END);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        for (int side = 0; side < 6; side++) {
            pose.pushPose();
            if (side == 1) pose.mulPose(Vector3f.XP.rotationDegrees(90));
            if (side == 2) pose.mulPose(Vector3f.XP.rotationDegrees(-90));
            if (side == 3) pose.mulPose(Vector3f.XP.rotationDegrees(180));
            if (side == 4) pose.mulPose(Vector3f.ZP.rotationDegrees(90));
            if (side == 5) pose.mulPose(Vector3f.ZP.rotationDegrees(-90));
            Matrix4f matrix = pose.last().pose(); BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(matrix,-100,-100,-100).uv(0,0).color(tint.red(),tint.green(),tint.blue(),1).endVertex();
            buffer.vertex(matrix,-100,-100,100).uv(0,16).color(tint.red(),tint.green(),tint.blue(),1).endVertex();
            buffer.vertex(matrix,100,-100,100).uv(16,16).color(tint.red(),tint.green(),tint.blue(),1).endVertex();
            buffer.vertex(matrix,100,-100,-100).uv(16,0).color(tint.red(),tint.green(),tint.blue(),1).endVertex();
            BufferUploader.drawWithShader(buffer.end()); pose.popPose();
        }
    }
}
