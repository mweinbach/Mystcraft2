package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.world.World;

public final class ClusterMissileItem extends Item {
  public ClusterMissileItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(16));
  }

  @Override
  public void appendTooltip(
      ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
    ClusterPlan plan = ClusterPlan.fromStack(stack);
    tooltip.add(
        Text.translatable(
            "tooltip.icbm_port.cluster_contents", plan.size(), ClusterPlan.MAX_BOMBLETS));
    if (plan.isEmpty()) tooltip.add(Text.translatable("tooltip.icbm_port.cluster_load"));
  }
}
