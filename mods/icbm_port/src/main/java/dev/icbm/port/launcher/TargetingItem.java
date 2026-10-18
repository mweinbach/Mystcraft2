package dev.icbm.port.launcher;

import dev.icbm.port.IcbmPort;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

final class TargetingItem extends Item {
  private final boolean laser;

  TargetingItem(boolean laser) {
    super(new Settings().group(IcbmPort.GROUP).maxCount(1));
    this.laser = laser;
  }

  @Override
  public ActionResult useOnBlock(ItemUsageContext context) {
    World world = context.getWorld();
    ItemStack stack = context.getStack();
    BlockPos pos = context.getBlockPos();
    if (!world.isClient) {
      if (world.getBlockEntity(pos) instanceof LauncherBlockEntity launcher
          && !context.getPlayer().isSneaking()) {
        var recorded = stack.getSubNbt("IcbmTarget");
        boolean ender =
            IcbmPort.payloadForMissile(launcher.getStack(0))
                == dev.icbm.port.payload.PayloadType.ENDER;
        BlockPos target =
            recorded != null && recorded.contains("Position")
                ? BlockPos.fromLong(recorded.getLong("Position"))
                : null;
        var dimension =
            recorded == null
                ? null
                : net.minecraft.util.Identifier.tryParse(recorded.getString("Dimension"));
        boolean success =
            ender
                ? target != null
                    && dimension != null
                    && launcher.configurePayloadDestination(
                        net.minecraft.util.registry.RegistryKey.of(
                            net.minecraft.util.registry.Registry.WORLD_KEY, dimension),
                        target)
                : target != null
                    && world.getRegistryKey().getValue().equals(dimension)
                    && launcher.configure(target, true, launcher.frequency());
        context
            .getPlayer()
            .sendMessage(
                Text.literal(
                    success
                        ? (ender ? "Ender destination copied: " : "Launcher target copied: ")
                            + target.toShortString()
                        : "Record a valid destination first"),
                true);
      } else {
        LauncherContent.writeTarget(world, stack, pos);
        context
            .getPlayer()
            .sendMessage(Text.literal("Target recorded: " + pos.toShortString()), true);
      }
    }
    return ActionResult.success(world.isClient);
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    if (!world.isClient) {
      HitResult hit = laser ? player.raycast(256, 0, false) : null;
      BlockPos target =
          hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
              ? block.getBlockPos()
              : player.getBlockPos();
      LauncherContent.writeTarget(world, stack, target);
      player.sendMessage(Text.literal("Target recorded: " + target.toShortString()), true);
    }
    return TypedActionResult.success(stack, world.isClient);
  }
}
