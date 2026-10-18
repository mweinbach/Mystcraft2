package dev.icbm.port.defense;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.*;

public final class DefenseMachineBlock extends Block {
  public enum Kind {
    RADAR,
    EMP,
    SAM
  }

  public static final IntProperty POWER = IntProperty.of("power", 0, 15);
  private final Kind kind;

  DefenseMachineBlock(Kind kind) {
    super(Settings.of(Material.METAL).strength(4, 20).requiresTool());
    this.kind = kind;
    setDefaultState(stateManager.getDefaultState().with(POWER, 0));
  }

  @Override
  protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
    builder.add(POWER);
  }

  @Override
  public void onBlockAdded(
      BlockState state, World world, BlockPos pos, BlockState old, boolean notify) {
    if (!world.isClient) world.createAndScheduleBlockTick(pos, this, 10);
  }

  @Override
  public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
    if (kind == Kind.RADAR) {
      int count =
          DefenseContent.isEmpDisabled(world, pos)
              ? 0
              : Math.min(
                  15, DefenseContent.trackedMissiles(world, Vec3d.ofCenter(pos), 128).size());
      if (count != state.get(POWER))
        world.setBlockState(pos, state.with(POWER, count), Block.NOTIFY_ALL);
    } else if (kind == Kind.EMP && world.isReceivingRedstonePower(pos)) {
      DefenseContent.applyEmp(world, Vec3d.ofCenter(pos), 32);
    } else if (kind == Kind.SAM
        && state.get(POWER) > 0
        && world.isReceivingRedstonePower(pos)
        && !DefenseContent.isEmpDisabled(world, pos)) {
      var targets =
          DefenseContent.trackedMissiles(world, Vec3d.ofCenter(pos), 64).stream()
              .filter(e -> DefenseContent.isThreat(world, Vec3d.ofCenter(pos), e))
              .toList();
      if (!targets.isEmpty()) {
        var target =
            targets.stream()
                .min(
                    java.util.Comparator.comparingDouble(
                        e -> e.squaredDistanceTo(Vec3d.ofCenter(pos))))
                .orElseThrow();
        var interceptor = new SamInterceptorEntity(DefenseContent.SAM_INTERCEPTOR, world);
        interceptor.setPosition(Vec3d.ofCenter(pos.up()));
        interceptor.setTarget(target);
        interceptor.setVelocity(
            target.getPos().subtract(interceptor.getPos()).normalize().multiply(3));
        if (world.spawnEntity(interceptor))
          world.setBlockState(pos, state.with(POWER, state.get(POWER) - 1), Block.NOTIFY_ALL);
      }
    }
    world.createAndScheduleBlockTick(pos, this, 20);
  }

  @Override
  public boolean emitsRedstonePower(BlockState state) {
    return kind == Kind.RADAR;
  }

  @Override
  public int getWeakRedstonePower(
      BlockState state, BlockView world, BlockPos pos, Direction direction) {
    return kind == Kind.RADAR ? state.get(POWER) : 0;
  }

  @Override
  public ActionResult onUse(
      BlockState state,
      World world,
      BlockPos pos,
      PlayerEntity player,
      Hand hand,
      BlockHitResult hit) {
    if (!world.isClient) {
      if (kind == Kind.SAM) {
        ItemStack ammo = player.getStackInHand(hand);
        if (ammo.isOf(Items.FIREWORK_ROCKET) && state.get(POWER) < 15) {
          world.setBlockState(pos, state.with(POWER, state.get(POWER) + 1), Block.NOTIFY_ALL);
          if (!player.getAbilities().creativeMode) ammo.decrement(1);
          player.sendMessage(
              Text.literal("Interceptor loaded: " + (state.get(POWER) + 1) + "/15"), true);
        } else
          player.sendMessage(
              Text.literal(
                  "Interceptors: "
                      + state.get(POWER)
                      + "/15. Load firework rockets, then apply redstone. Range: 64 blocks"),
              true);
      } else if (kind == Kind.RADAR) {
        var targets = DefenseContent.trackedMissiles(world, Vec3d.ofCenter(pos), 128);
        player.sendMessage(
            Text.literal(
                "Radar: "
                    + (DefenseContent.isEmpDisabled(world, pos)
                        ? "EMP disabled"
                        : targets.size() + " loaded missiles within 128 blocks")),
            false);
        targets.stream()
            .limit(8)
            .forEach(
                e ->
                    player.sendMessage(
                        Text.literal("Missile " + e.getBlockPos().toShortString()), false));
      } else
        player.sendMessage(
            Text.literal("EMP tower: redstone pulses every second; radius 32 blocks"), true);
    }
    return ActionResult.success(world.isClient);
  }
}
