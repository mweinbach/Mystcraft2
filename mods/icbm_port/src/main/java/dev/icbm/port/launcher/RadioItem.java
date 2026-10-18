package dev.icbm.port.launcher;

import dev.icbm.port.IcbmPort;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class RadioItem extends Item {
  RadioItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(1));
  }

  @Override
  public ActionResult useOnBlock(ItemUsageContext context) {
    if (!(context.getWorld().getBlockEntity(context.getBlockPos())
        instanceof LauncherBlockEntity launcher)) return ActionResult.PASS;
    if (!context.getWorld().isClient) {
      LauncherContent.writeTarget(context.getWorld(), context.getStack(), context.getBlockPos());
      context.getStack().getOrCreateNbt().putInt("Frequency", launcher.frequency());
      context
          .getPlayer()
          .sendMessage(
              Text.literal("Radio paired to launcher frequency " + launcher.frequency()), true);
    }
    return ActionResult.success(context.getWorld().isClient);
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    BlockPos pos = LauncherContent.readTarget(world, stack);
    if (world instanceof ServerWorld server
        && pos != null
        && player.squaredDistanceTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 256 * 256
        && world.isChunkLoaded(pos)
        && world.getBlockEntity(pos) instanceof LauncherBlockEntity launcher
        && launcher.frequency() == stack.getOrCreateNbt().getInt("Frequency")
        && !player.isSpectator()) {
      boolean launched = launcher.launcherBlock().launch(server, pos);
      player.sendMessage(
          Text.literal(launched ? "Radio launch accepted" : "Launcher not ready"), true);
      player.getItemCooldownManager().set(this, 20);
    }
    return TypedActionResult.success(stack, world.isClient);
  }
}
