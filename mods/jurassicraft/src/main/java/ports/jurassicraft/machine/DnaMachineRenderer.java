package ports.jurassicraft.machine;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.client.TabulaParts;

/** Displays the sample items and mode-dependent screens from the original lab machines. */
public final class DnaMachineRenderer implements BlockEntityRenderer<DnaMachineBlockEntity> {
    private static final ResourceLocation COMBINATOR_TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/blocks/dna_combinator_hybridizer.png");
    private final ItemRenderer items;
    private final TabulaParts combinatorScreens = new TabulaParts("models/machine/dna_combinator_screens.json");
    private final TabulaParts hybridizerScreens = new TabulaParts("models/machine/dna_hybridizer_screens.json");

    public DnaMachineRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }

    @Override public void render(DnaMachineBlockEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (machine.getLevel() == null || !(machine.getBlockState().getBlock() instanceof DnaMachineBlock block)) return;
        Direction facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        if (block.kind() == DnaMachineKind.EXTRACTOR) {
            if (machine.getItem(0).isEmpty()) return;
            if (facing == Direction.EAST || facing == Direction.WEST) facing = facing.getOpposite();
            pose.pushPose();
            pose.translate(.5, 1.5, .5);
            pose.mulPose(Vector3f.YP.rotationDegrees(facing.get2DDataValue() * 90 - 180));
            pose.scale(-1, -1, 1);
            pose.translate(.225, 1.25, -.125);
            pose.mulPose(Vector3f.XP.rotationDegrees(-90));
            pose.scale(-.375F, -.375F, .375F);
            // Upstream renders the raw item model. FIXED applies the item-frame transform.
            items.renderStatic(machine.getItem(0), ItemTransforms.TransformType.NONE, light, overlay, pose, buffers, 0);
            pose.popPose();
        } else if (block.kind() == DnaMachineKind.SEQUENCER) {
            if (facing == Direction.NORTH || facing == Direction.SOUTH) facing = facing.getOpposite();
            pose.pushPose();
            pose.translate(.5, 1.5, .5);
            pose.mulPose(Vector3f.YP.rotationDegrees(facing.get2DDataValue() * 90));
            pose.translate(-.2, -.6, .15);
            pose.scale(-.375F, -.375F, .375F);
            pose.mulPose(Vector3f.XP.rotationDegrees(90));
            Player player = Minecraft.getInstance().player;
            float age = (player != null ? player.tickCount : machine.getLevel().getGameTime()) + partialTick;
            pose.translate(0, 0, Mth.sin(age * .05F) * .025F);
            for (int slot = 0; slot < 6; slot++) {
                pose.translate(0, 0, -.4);
                if (slot % 2 == 0 && !machine.getItem(slot).isEmpty())
                    items.renderStatic(machine.getItem(slot), ItemTransforms.TransformType.NONE, light, overlay, pose, buffers, slot);
            }
            pose.popPose();
        } else if (block.kind() == DnaMachineKind.COMBINATOR) {
            TabulaParts screens = machine.hybridizerMode() ? hybridizerScreens : combinatorScreens;
            screens.show("Screen 1", "Screen 2");
            var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(COMBINATOR_TEXTURE));
            screens.render(pose, buffer, light, overlay, facing, true);
        }
    }
}
