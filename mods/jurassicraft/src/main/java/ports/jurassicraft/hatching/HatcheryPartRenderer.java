package ports.jurassicraft.hatching;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.client.TabulaParts;

/**
 * Inventory parts that 1.12.2 swapped in through blockstate models.
 * Embryonic slot 0 (DNA) shows the petri-dish cubes and slot 1 (the dish) shows the tube rack,
 * matching the upstream property names. A placed calcification machine always shows the egg:
 * upstream compared the slots with {@code != null}, and an empty stack is not null.
 */
public final class HatcheryPartRenderer implements BlockEntityRenderer<HatcheryBlockEntity> {
    private static final ResourceLocation EMBRYONIC_TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/blocks/embryonic_machine.png");
    private static final ResourceLocation EGG_TEXTURE = new ResourceLocation(PortMod.MOD_ID, "textures/blocks/embryo_calcification_machine.png");
    private final TabulaParts embryonic = new TabulaParts("models/machine/embryonic_machine_parts.json");
    private final TabulaParts egg = new TabulaParts("models/machine/embryo_calcification_egg.json");

    public HatcheryPartRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HatcheryBlockEntity machine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var state = machine.getBlockState();
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        if (state.is(HatcheryRegistry.block(HatcheryRegistry.Machine.EMBRYONIC))) {
            List<String> cubes = new ArrayList<>();
            if (machine.visualDish()) {
                cubes.add("Petri dish 1");
                cubes.add("Petri dish 2");
            }
            if (machine.visualTubes()) {
                cubes.add("Test tube rack 1");
                cubes.add("Test tube rack 2");
            }
            if (cubes.isEmpty()) return;
            embryonic.show(cubes.toArray(String[]::new));
            var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(EMBRYONIC_TEXTURE));
            embryonic.render(pose, buffer, light, overlay, facing, true);
        } else if (state.is(HatcheryRegistry.block(HatcheryRegistry.Machine.CALCIFIER))) {
            egg.show("Egg base", "shape21");
            var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(EGG_TEXTURE));
            egg.render(pose, buffer, light, overlay, facing, true);
        }
    }
}
