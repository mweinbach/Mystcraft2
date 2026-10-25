package dev.icbm.port.launcher;

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
    // Tools act in their own useOnBlock (GPS/radar gun record this position; component blocks are
    // not radios, so a detonator falls through to its normal use), exactly as on any other block.
    if (LauncherContent.isBlockTool(player.getStackInHand(hand))) return ActionResult.PASS;
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

  /**
   * Launcher screens fire every launcher in their network while they receive strong redstone
   * power, re-checked every 3 ticks (adapted from MIT ICBM-Classic {@code
   * content/blocks/launcher/screen/TileLauncherScreen#update}, which tests {@code getStrongPower}
   * every third tick). Launchers that already fired are simply empty until reloaded.
   */
  @Override
  public void neighborUpdate(
      BlockState state,
      net.minecraft.world.World world,
      BlockPos pos,
      Block source,
      BlockPos sourcePos,
      boolean notify) {
    if (role != Role.SCREEN || world.isClient) return;
    boolean powered = world.getReceivedStrongRedstonePower(pos) > 0;
    if (state.get(POWERED) == powered) return;
    world.setBlockState(pos, state.with(POWERED, powered), 3);
    if (powered && !world.getBlockTickScheduler().isQueued(pos, this))
      world.createAndScheduleBlockTick(pos, this, 1);
  }

  @Override
  public void scheduledTick(
      BlockState state, ServerWorld world, BlockPos pos, net.minecraft.util.math.random.Random random) {
    if (role != Role.SCREEN) return;
    if (world.getReceivedStrongRedstonePower(pos) <= 0) {
      if (state.get(POWERED)) world.setBlockState(pos, state.with(POWERED, false), 3);
      return;
    }
    LauncherNetwork.fireAll(world, pos);
    world.createAndScheduleBlockTick(pos, this, SCREEN_REDSTONE_TICKS);
  }

  /** Upstream screen redstone poll interval. */
  public static final int SCREEN_REDSTONE_TICKS = 3;
}
