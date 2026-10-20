package ports.buildcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.ClientArchitectTables;

/**
 * Upstream {@code RenderArchitectTables}: blocks an architect table scanned recently glow through the world,
 * fading out over {@link ClientArchitectTables#START_SCANNED_BLOCK_VALUE} ticks. Only blocks inside a box a
 * table is scanning are drawn. Upstream could clip them to the box with the stencil buffer when its config
 * allowed; this is its other path, which draws over everything.
 */
public final class ArchitectTablesRenderer {
    public static final ResourceLocation SCAN = BuildCraft.id("textures/block/scan.png");
    private static final float[][][] FACES = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}};
    private static final float[][] UVS = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};

    private ArchitectTablesRenderer() {}

    /** {@code poseStack} carries the camera's rotation but not its position. */
    public static void render(PoseStack poseStack, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long now = minecraft.level.getGameTime();
        ClientArchitectTables.expire(now);
        if (ClientArchitectTables.BOXES.isEmpty() || ClientArchitectTables.SCANNED_BLOCKS.isEmpty()) return;
        Vec3 cameraPos = camera.getPosition();
        List<AABB> boxes = new ArrayList<>(ClientArchitectTables.BOXES.keySet());
        boxes.sort(Comparator.<AABB>comparingDouble(box -> box.getCenter().distanceTo(cameraPos)).reversed());
        List<BlockPos> poses = new ArrayList<>(ClientArchitectTables.SCANNED_BLOCKS.keySet());
        poses.sort(Comparator.<BlockPos>comparingDouble(pos -> Vec3.atLowerCornerOf(pos).distanceTo(cameraPos)).reversed());

        poseStack.pushPose();
        BlockPos origin = camera.getBlockPosition();
        poseStack.translate(origin.getX() - cameraPos.x, origin.getY() - cameraPos.y, origin.getZ() - cameraPos.z);
        Matrix4f pose = poseStack.last().pose();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, SCAN);
        for (AABB box : boxes) {
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (BlockPos pos : poses) {
                if (!box.intersects(new AABB(pos))) continue;
                int alpha = ClientArchitectTables.scannedBlockValue(pos, now) * 50 / ClientArchitectTables.START_SCANNED_BLOCK_VALUE;
                if (alpha <= 0) continue;
                int x = pos.getX() - origin.getX(), y = pos.getY() - origin.getY(), z = pos.getZ() - origin.getZ();
                for (Direction face : Direction.values()) {
                    float[][] corners = FACES[face.get3DDataValue()];
                    for (int i = 0; i < 4; i++) {
                        buffer.vertex(pose, x + corners[i][0], y + corners[i][1], z + corners[i][2])
                                .uv(UVS[i][0], UVS[i][1])
                                .color(255, 255, 255, alpha)
                                .endVertex();
                    }
                }
            }
            Tesselator.getInstance().end();
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }
}
