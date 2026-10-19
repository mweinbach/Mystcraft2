package ports.jurassicraft.tourrail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.tourrail.TourRailBlock;
import ports.jurassicraft.tourrail.TourRailBlockEntity;
import ports.jurassicraft.tourrail.TourRailRegistry;
import ports.jurassicraft.tourrail.TourRailShape;
import ports.jurassicraft.vehicle.client.VehicleModel;

/** Renders the original Tabula track geometry and speed stripe textures. */
public final class TourRailRenderer implements BlockEntityRenderer<TourRailBlockEntity> {
    private record Model(String family, int rotation) {}
    private final Map<String, VehicleModel> models = new HashMap<>();

    private static Model model(TourRailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> new Model("straight", 0);
            case EAST_WEST -> new Model("straight", 90);
            case NORTH_EAST -> new Model("corner", 90);
            case NORTH_WEST -> new Model("corner", 0);
            case SOUTH_WEST -> new Model("corner", 270);
            case SOUTH_EAST -> new Model("corner", 180);
            case ASCENDING_NORTH -> new Model("slope", 180);
            case ASCENDING_WEST -> new Model("slope", 90);
            case ASCENDING_SOUTH -> new Model("slope", 0);
            case ASCENDING_EAST -> new Model("slope", 270);
            case DIAGONAL_NE_SW -> new Model("diagonal", 0);
            case DIAGONAL_NW_SE -> new Model("diagonal", 90);
            case HORIZONTAL_NE -> new Model("diagonal_turn_left", 270);
            case HORIZONTAL_SE -> new Model("diagonal_turn_right", 270);
            case HORIZONTAL_NW -> new Model("diagonal_turn_right", 90);
            case HORIZONTAL_SW -> new Model("diagonal_turn_left", 90);
            case VERTICAL_NE -> new Model("diagonal_turn_right", 180);
            case VERTICAL_SE -> new Model("diagonal_turn_left", 0);
            case VERTICAL_NW -> new Model("diagonal_turn_left", 180);
            case VERTICAL_SW -> new Model("diagonal_turn_right", 0);
        };
    }
    @Override public void render(TourRailBlockEntity blockEntity, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        var state = blockEntity.getBlockState();
        if (!(state.getBlock() instanceof TourRailBlock block)) return;
        Model spec = model(state.getValue(TourRailBlock.SHAPE));
        VehicleModel geometry = models.computeIfAbsent(spec.family(), key -> new VehicleModel("tour_rail_" + key));
        pose.pushPose();
        pose.translate(.5, 1.5, .5);
        // Blockstate y rotations run clockwise when viewed from above.
        pose.mulPose(Vector3f.YP.rotationDegrees(-spec.rotation()));
        pose.scale(-1, -1, 1);
        ResourceLocation base = new ResourceLocation(PortMod.MOD_ID, "textures/blocks/tour_rail_" + spec.family() + ".png");
        geometry.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(base)), light);
        if (state.getBlock() != TourRailRegistry.RAIL.get()) {
            int color = block.stripeColor();
            ResourceLocation stripe = new ResourceLocation(PortMod.MOD_ID, "textures/blocks/tour_rail_" + spec.family() + "_stripe.png");
            geometry.render(pose, buffers.getBuffer(RenderType.entityTranslucent(stripe)), light,
                    ((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1);
        }
        pose.popPose();
    }
}
