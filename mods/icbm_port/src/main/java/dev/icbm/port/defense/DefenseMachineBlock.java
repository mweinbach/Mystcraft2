package dev.icbm.port.defense;

import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

public final class DefenseMachineBlock extends BlockWithEntity {
  public enum Kind {
    RADAR,
    EMP,
    SAM
  }

  public static final IntProperty POWER = IntProperty.of("power", 0, 15);

  /** SAM stations store their ammunition in {@link #POWER}, so the magazine holds 15 missiles. */
  public static final int MAX_SAM_AMMO = 15;
  private final Kind kind;

  DefenseMachineBlock(Kind kind) {
    super(Settings.of(Material.METAL).strength(4, 20).requiresTool());
    this.kind = kind;
    setDefaultState(stateManager.getDefaultState().with(POWER, 0));
  }

  public Kind kind() {
    return kind;
  }

  @Override
  protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
    builder.add(POWER);
  }

  @Override
  public BlockRenderType getRenderType(BlockState state) {
    return BlockRenderType.MODEL;
  }

  @Override
  public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
    return new DefenseMachineBlockEntity(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      World world, BlockState state, BlockEntityType<T> type) {
    return world.isClient
        ? null
        : checkType(type, DefenseContent.MACHINE_BLOCK_ENTITY, DefenseMachineBlockEntity::tick);
  }

  @Override
  public boolean emitsRedstonePower(BlockState state) {
    return kind == Kind.RADAR;
  }

  @Override
  public int getWeakRedstonePower(
      BlockState state,
      BlockView world,
      BlockPos pos,
      net.minecraft.util.math.Direction direction) {
    return kind == Kind.RADAR ? state.get(POWER) : 0;
  }

  /** Upstream {@code BlockRadarStation} reports the same strong and weak signal. */
  @Override
  public int getStrongRedstonePower(
      BlockState state,
      BlockView world,
      BlockPos pos,
      net.minecraft.util.math.Direction direction) {
    return getWeakRedstonePower(state, world, pos, direction);
  }

  @Override
  public void onStateReplaced(
      BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
    if (!state.isOf(newState.getBlock())) {
      if (world.getBlockEntity(pos) instanceof DefenseMachineBlockEntity machine)
        ItemScatterer.spawn(world, pos, machine.batterySlot());
      if (kind == Kind.SAM && state.get(POWER) > 0)
        Block.dropStack(
            world,
            pos,
            new net.minecraft.item.ItemStack(DefenseContent.SAM_MISSILE, state.get(POWER)));
      world.updateComparators(pos, this);
    }
    super.onStateReplaced(state, world, pos, newState, moved);
  }

  @Override
  public ActionResult onUse(
      BlockState state,
      World world,
      BlockPos pos,
      PlayerEntity player,
      Hand hand,
      BlockHitResult hit) {
    if (player.isSpectator() || !player.canModifyBlocks() || !world.canPlayerModifyAt(player, pos))
      return ActionResult.FAIL;
    if (player.getStackInHand(hand).isOf(DefenseContent.EMP_TOWER_EXTENSION.asItem()))
      return ActionResult.PASS;
    // Detonators copy this machine's channel, the GPS/radar gun record its position and batteries
    // charge it, all in the item's useOnBlock, which vanilla only reaches if this returns PASS.
    // Launchers already pass every item they don't consume, so this matches them.
    var held = player.getStackInHand(hand);
    if (dev.icbm.port.launcher.LauncherContent.isBlockTool(held)
        || held.getItem() instanceof dev.icbm.port.energy.BatteryItem) return ActionResult.PASS;
    // Upstream BlockRadarStation#onBlockActivated: redstone dust toggles the redstone output.
    if (kind == Kind.RADAR && player.getStackInHand(hand).isOf(net.minecraft.item.Items.REDSTONE)) {
      if (!world.isClient && world.getBlockEntity(pos) instanceof DefenseMachineBlockEntity machine)
        player.sendMessage(
            net.minecraft.text.Text.translatable(
                machine.toggleRedstoneOutput()
                    ? "message.icbm_port.radar.redstone_on"
                    : "message.icbm_port.radar.redstone_off"),
            true);
      return ActionResult.success(world.isClient);
    }
    if (!world.isClient && world.getBlockEntity(pos) instanceof DefenseMachineBlockEntity machine) {
      var ammo = player.getStackInHand(hand);
      if (kind == Kind.SAM
          && ammo.isOf(DefenseContent.SAM_MISSILE)
          && state.get(POWER) < MAX_SAM_AMMO) {
        world.setBlockState(pos, state.with(POWER, state.get(POWER) + 1), Block.NOTIFY_ALL);
        if (!player.getAbilities().creativeMode) ammo.decrement(1);
      } else player.openHandledScreen(machine);
    }
    return ActionResult.success(world.isClient);
  }
}
