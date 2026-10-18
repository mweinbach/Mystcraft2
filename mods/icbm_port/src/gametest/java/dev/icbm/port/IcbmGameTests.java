package dev.icbm.port;

import dev.icbm.port.block.LauncherBlock;
import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class IcbmGameTests implements FabricGameTest {
  private static void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  private BlockPos launcher(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context
        .getWorld()
        .setBlockState(
            pos,
            IcbmPort.LAUNCHER
                .getDefaultState()
                .with(LauncherBlock.FACING, Direction.UP)
                .with(LauncherBlock.LOADED, true),
            Block.NOTIFY_ALL);
    return pos;
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void launchConsumesExactlyOneMissile(TestContext context) {
    BlockPos pos = launcher(context);
    check(context, IcbmPort.LAUNCHER.launch(context.getWorld(), pos), "Loaded launcher must fire");
    check(
        context,
        !context.getWorld().getBlockState(pos).get(LauncherBlock.LOADED),
        "Missile must be consumed");
    check(
        context,
        !IcbmPort.LAUNCHER.launch(context.getWorld(), pos),
        "Empty launcher must not fire again");
    var missiles =
        context
            .getWorld()
            .getEntitiesByType(IcbmPort.MISSILE_ENTITY, new Box(pos).expand(3), e -> true);
    check(context, missiles.size() == 1, "Exactly one missile must spawn");
    check(
        context,
        Math.abs(missiles.get(0).getVelocity().y - IcbmPort.MISSILE_SPEED) < 1e-6,
        "Missile must follow launcher facing");
    missiles.forEach(MissileEntity::discard);
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void blockedMuzzlePreservesAmmo(TestContext context) {
    BlockPos pos = launcher(context);
    context.getWorld().setBlockState(pos.up(), Blocks.STONE.getDefaultState());
    check(
        context,
        !IcbmPort.LAUNCHER.launch(context.getWorld(), pos),
        "Blocked launcher must not fire");
    check(
        context,
        context.getWorld().getBlockState(pos).get(LauncherBlock.LOADED),
        "Blocked launch must preserve ammunition");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void redstonePulseFiresOnce(TestContext context) {
    BlockPos pos = launcher(context);
    context
        .getWorld()
        .setBlockState(pos.down(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
    context.runAtTick(
        6,
        () -> {
          check(
              context,
              !context.getWorld().getBlockState(pos).get(LauncherBlock.LOADED),
              "Redstone must fire after the scheduled delay");
          context
              .getWorld()
              .setBlockState(
                  pos,
                  context.getWorld().getBlockState(pos).with(LauncherBlock.LOADED, true),
                  Block.NOTIFY_ALL);
          IcbmPort.LAUNCHER.neighborUpdate(
              context.getWorld().getBlockState(pos),
              context.getWorld(),
              pos,
              Blocks.REDSTONE_BLOCK,
              pos.down(),
              false);
        });
    context.runAtTick(
        12,
        () -> {
          check(
              context,
              context.getWorld().getBlockState(pos).get(LauncherBlock.LOADED),
              "Continuous power must not fire a second missile");
          context
              .getWorld()
              .getEntitiesByType(IcbmPort.MISSILE_ENTITY, new Box(pos).expand(32), e -> true)
              .forEach(MissileEntity::discard);
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void explosivePrimesWithRedstone(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context.getWorld().setBlockState(pos, IcbmPort.CONDENSED_EXPLOSIVE.getDefaultState());
    context
        .getWorld()
        .setBlockState(pos.down(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
    check(context, context.getWorld().isAir(pos), "Priming must remove the block");
    var explosives =
        context
            .getWorld()
            .getEntitiesByType(IcbmPort.PRIMED_EXPLOSIVE, new Box(pos).expand(1), e -> true);
    check(context, explosives.size() == 1, "Priming must spawn one explosive");
    check(
        context,
        explosives.get(0).getFuse() == dev.icbm.port.payload.PayloadType.CONDENSED.blockFuseTicks(),
        "Priming must use the configured fuse");
    explosives.forEach(PrimedExplosiveEntity::discard);
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void fuseSurvivesSaveReload(TestContext context) {
    var original = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, context.getWorld());
    original.setFuse(23);
    NbtCompound saved = original.writeNbt(new NbtCompound());
    var restored = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, context.getWorld());
    restored.readNbt(saved);
    check(context, restored.getFuse() == 23, "Reload must not reset the countdown");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void missileDetonatesOnCollision(TestContext context) {
    BlockPos start = context.getAbsolutePos(new BlockPos(2, 20, 2));
    context.getWorld().setBlockState(start.south(3), Blocks.STONE.getDefaultState());
    var missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    missile.setPosition(start.getX() + 0.5, start.getY() + 0.5, start.getZ() + 0.5);
    missile.setVelocity(0, 0, IcbmPort.MISSILE_SPEED);
    context.getWorld().spawnEntity(missile);
    context.runAtTick(
        5,
        () -> {
          check(context, missile.isRemoved(), "Impact must remove the missile");
          check(
              context,
              context.getWorld().isAir(start.south(3)),
              "Impact must detonate the payload");
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void fuseEndsInExplosion(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 20, 2));
    context.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState());
    var explosive = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, context.getWorld());
    explosive.setPosition(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    explosive.setNoGravity(true);
    explosive.setFuse(2);
    context.getWorld().spawnEntity(explosive);
    context.runAtTick(
        5,
        () -> {
          check(context, explosive.isRemoved(), "Expired fuse must remove the primed entity");
          check(context, context.getWorld().isAir(pos), "Expired fuse must explode");
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void expiredMissileIsRemovedAfterReload(TestContext context) {
    var missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    missile.setPosition(
        context.getAbsolutePos(new BlockPos(2, 3, 2)).getX(),
        context.getAbsolutePos(new BlockPos(2, 3, 2)).getY(),
        context.getAbsolutePos(new BlockPos(2, 3, 2)).getZ());
    NbtCompound saved = new NbtCompound();
    missile.writeCustomDataToNbt(saved);
    saved.putInt("FlightTicks", IcbmPort.MISSILE_LIFETIME);
    missile.readCustomDataFromNbt(saved);
    context.getWorld().spawnEntity(missile);
    context.runAtTick(
        2,
        () -> {
          check(context, missile.isRemoved(), "Reload must not extend the maximum flight time");
          context.complete();
        });
  }
}
