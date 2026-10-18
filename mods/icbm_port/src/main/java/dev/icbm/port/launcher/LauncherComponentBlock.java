package dev.icbm.port.launcher;

import dev.icbm.port.block.LauncherBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

public final class LauncherComponentBlock extends Block {
  public enum Role {
    FRAME,
    SUPPORT,
    SCREEN,
    CONNECTOR
  }

  public static final net.minecraft.state.property.BooleanProperty POWERED =
      net.minecraft.state.property.Properties.POWERED;
  private static final java.util.Map<java.util.UUID, Integer> SELECTION = new java.util.HashMap<>();
  private final Role role;

  public LauncherComponentBlock(Settings settings, Role role) {
    super(settings);
    this.role = role;
    setDefaultState(getStateManager().getDefaultState().with(POWERED, false));
  }

  @Override
  protected void appendProperties(
      net.minecraft.state.StateManager.Builder<Block, BlockState> builder) {
    builder.add(POWERED);
  }

  @Override
  public ActionResult onUse(
      BlockState state,
      net.minecraft.world.World world,
      BlockPos pos,
      PlayerEntity player,
      Hand hand,
      BlockHitResult hit) {
    if (world.isClient) return ActionResult.SUCCESS;
    if (player.isSpectator()) return ActionResult.PASS;
    var launchers =
        LauncherNetwork.connected(world, pos).stream()
            .filter(p -> world.getBlockEntity(p) instanceof LauncherBlockEntity)
            .toList();
    if (launchers.isEmpty()) {
      player.sendMessage(Text.literal("No launcher connected (32 block / 128 node limit)"), true);
      return ActionResult.CONSUME;
    }
    // Sneaking cycles deterministically through launchers without retaining a stale network cache.
    if (SELECTION.size() > 1024) SELECTION.clear();
    int index =
        Math.floorMod(
            SELECTION.getOrDefault(player.getUuid(), 0) + (player.isSneaking() ? 1 : 0),
            launchers.size());
    SELECTION.put(player.getUuid(), index);
    LauncherBlockEntity launcher = (LauncherBlockEntity) world.getBlockEntity(launchers.get(index));
    ItemStack stack = player.getStackInHand(hand);
    if (role == Role.SUPPORT && stack.isOf(Items.REDSTONE)) {
      for (BlockPos candidate : launchers) {
        LauncherBlockEntity target = (LauncherBlockEntity) world.getBlockEntity(candidate);
        if (target.acceptCharge()) {
          if (!player.getAbilities().creativeMode) stack.decrement(1);
          break;
        }
      }
    } else if (role == Role.SCREEN) player.openHandledScreen(launcher);
    else
      player.sendMessage(
          Text.literal(
              "Connected launchers: "
                  + launchers.size()
                  + "; selected "
                  + launcher.getPos().toShortString()
                  + "; charge "
                  + launcher.energy()),
          true);
    return ActionResult.CONSUME;
  }

  @Override
  public void neighborUpdate(
      BlockState state,
      net.minecraft.world.World world,
      BlockPos pos,
      Block source,
      BlockPos sourcePos,
      boolean notify) {
    if (role != Role.SCREEN || world.isClient) return;
    boolean powered = world.isReceivingRedstonePower(pos);
    if (state.get(POWERED) == powered) return;
    world.setBlockState(pos, state.with(POWERED, powered), 3);
    // Screen redstone is a control signal for its connected launcher network; bases retain
    // edge-triggered firing.
    if (role == Role.SCREEN
        && world instanceof ServerWorld server
        && world.isReceivingRedstonePower(pos)) {
      for (BlockPos target : LauncherNetwork.connected(world, pos))
        if (world.getBlockState(target).getBlock() instanceof LauncherBlock launcher) {
          BlockState launcherState = world.getBlockState(target);
          if (!launcherState.get(LauncherBlock.TRIGGERED)) {
            world.setBlockState(target, launcherState.with(LauncherBlock.TRIGGERED, true), 3);
            world.createAndScheduleBlockTick(target, launcher, 4);
          }
        }
    } else if (role == Role.SCREEN && !world.isClient && !world.isReceivingRedstonePower(pos)) {
      for (BlockPos target : LauncherNetwork.connected(world, pos))
        if (world.getBlockState(target).getBlock() instanceof LauncherBlock
            && !world.isReceivingRedstonePower(target))
          world.setBlockState(
              target, world.getBlockState(target).with(LauncherBlock.TRIGGERED, false), 3);
    }
  }
}
