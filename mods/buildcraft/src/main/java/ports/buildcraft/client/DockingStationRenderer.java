package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.robotics.DockingStationPluggable;
import ports.buildcraft.transport.pluggable.PipePluggable;

/**
 * Draws the docking station with the plate-and-stem shape of upstream's {@code robot_station_base} model (built
 * facing west) and upstream's station textures; the plate's outer face shows whether a robot is docked, linked or
 * the station is free.
 */
public final class DockingStationRenderer {
    public static final ResourceLocation BASE = BuildCraft.id("block/robot_station/base");
    public static final ResourceLocation AVAILABLE = BuildCraft.id("block/robot_station/state_available");
    public static final ResourceLocation LINKED = BuildCraft.id("block/robot_station/state_linked");
    public static final ResourceLocation RESERVED = BuildCraft.id("block/robot_station/state_reserved");

    private DockingStationRenderer() {}

    public static List<ResourceLocation> sprites() {
        return List.of(BASE, AVAILABLE, LINKED, RESERVED);
    }

    static void render(PipePluggable plug, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        if (!(plug instanceof DockingStationPluggable station)) return;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        poseStack.pushPose();
        RenderUtil.rotateFromWestAny(poseStack, plug.side);
        RenderUtil.box(poseStack, consumer, RenderUtil.sprite(BASE), RenderUtil.AUTO, 3, 4, 4, 4.03F, 12, 12, 0xFFFFFFFF, light);
        RenderUtil.box(poseStack, consumer, RenderUtil.sprite(BASE), RenderUtil.AUTO, 0, 7, 7, 3, 9, 9, 0xFFFFFFFF, light);
        ResourceLocation state = station.hasDockedRobot() ? RESERVED : station.isLinked() ? LINKED : AVAILABLE;
        RenderUtil.face(poseStack, consumer, RenderUtil.sprite(state), Direction.WEST, new float[] {4, 4, 12, 12},
                2.99F, 4, 4, 4.03F, 12, 12, 0xFFFFFFFF, light);
        poseStack.popPose();
    }
}
