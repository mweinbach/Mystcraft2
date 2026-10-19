package ports.jurassicraft.hatching;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.client.DinosaurModel;
import ports.jurassicraft.content.Species;

/** Renders the five eggs using the original species geometry and placements. */
public final class IncubatorRenderer implements BlockEntityRenderer<HatcheryBlockEntity> {
    private final Map<Species, DinosaurModel> eggs = new EnumMap<>(Species.class);
    private static final double[][] POSITIONS = {{.2,.2}, {.2,.8}, {.5,.5}, {.8,.8}, {.8,.2}};
    public IncubatorRenderer(BlockEntityRendererProvider.Context context) {
        for (Species species : Species.values()) if (species != Species.COELACANTH) eggs.put(species, DinosaurModel.egg(species));
    }
    @Override public void render(HatcheryBlockEntity incubator, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (int slot = 0; slot < 5; slot++) {
            ItemStack stack = incubator.visualEgg(slot);
            if (stack.isEmpty()) continue;
            Species species = null;
            for (Species candidate : eggs.keySet())
                if (stack.is(HatcheryRegistry.egg(candidate)) || stack.is(HatcheryRegistry.hatchedEgg(candidate))) { species = candidate; break; }
            if (species == null) continue;
            pose.pushPose();
            pose.translate(POSITIONS[slot][0], slot == 2 ? 1.3 : 1.25, POSITIONS[slot][1]);
            pose.scale(-.5F, -.5F, -.5F);
            var texture = new ResourceLocation(PortMod.MOD_ID, "textures/entities/egg/" + species.id() + ".png");
            eggs.get(species).renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay, 1, 1, 1, 1);
            pose.popPose();
        }
    }
}
