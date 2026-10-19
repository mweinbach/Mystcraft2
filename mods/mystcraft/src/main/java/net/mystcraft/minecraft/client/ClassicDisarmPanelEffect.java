package net.mystcraft.minecraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.renderer.GameRenderer;

/** Pinned LinkRendererDisarm timing, endpoint selection, red halo and white lightning core. */
final class ClassicDisarmPanelEffect {
    private final Random random = new Random();
    private long lastTime = System.currentTimeMillis();
    private final List<Point> ends = new ArrayList<>();
    private Point center;
    private record Point(double x, double y) {}

    void render(PoseStack pose, int left, int top, int width, int height) {
        long now = System.currentTimeMillis(), delta = now - lastTime;
        if ((delta > 3000 && random.nextInt(3000) == 0) || delta > 8000) {
            center = new Point(random.nextInt(width / 2) + left + width / 4,
                    random.nextInt(height / 2) + top + height / 4);
            ends.clear();
            int offset = random.nextInt(4);
            for (int i = 0; i < random.nextInt(6) + 3; i++) ends.add(switch ((i + offset) % 4) {
                case 0 -> new Point(left, random.nextInt(height) + top);
                case 1 -> new Point(random.nextInt(width) + left, top);
                case 2 -> new Point(left + width, random.nextInt(height) + top);
                default -> new Point(random.nextInt(width) + left, top + height);
            });
            lastTime = now; delta = 0;
        }
        if (center == null) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Point start = new Point(center.x + random.nextGaussian() * 2, center.y + random.nextGaussian() * 2);
        for (Point end : ends) {
            Point finish = new Point(end.x + random.nextGaussian() * 3, end.y + random.nextGaussian() * 3);
            int count = random.nextInt(50);
            var points = new ArrayList<Point>(); points.add(start);
            for (int i = 1; i < count; i++) {
                // Source uses integer division here, so jitter remains two pixels along the bolt.
                double f = (double) i / count;
                points.add(new Point(start.x + (finish.x - start.x) * f + random.nextGaussian() * 2,
                        start.y + (finish.y - start.y) * f + random.nextGaussian() * 2));
            }
            points.add(finish);
            for (int pass = 0; pass < 2; pass++) for (int i = 1; i < points.size(); i++) {
                Point a = points.get(i - 1), b = points.get(i);
                double dx = b.x - a.x, dy = b.y - a.y, length = Math.hypot(dx, dy);
                if (length < 0.001) continue;
                double radius = pass == 0 ? 4 : 1, nx = -dy / length * radius, ny = dx / length * radius;
                int g = pass == 0 ? 51 : 255, blue = g;
                var matrix = pose.last().pose();
                builder.vertex(matrix, (float) (a.x + nx), (float) (a.y + ny), 0).color(255, g, blue, 255).endVertex();
                builder.vertex(matrix, (float) (a.x - nx), (float) (a.y - ny), 0).color(255, g, blue, 255).endVertex();
                builder.vertex(matrix, (float) (b.x - nx), (float) (b.y - ny), 0).color(255, g, blue, 255).endVertex();
                builder.vertex(matrix, (float) (b.x + nx), (float) (b.y + ny), 0).color(255, g, blue, 255).endVertex();
            }
        }
        Tesselator.getInstance().end();
        RenderSystem.disableBlend();
        if (delta > 300) center = null;
    }
}
