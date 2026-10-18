package dev.icbm.port.payload;

import dev.icbm.port.simulation.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.*;

public final class SimulationAssertions {
  private SimulationAssertions() {}

  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  public static void flyingInventoryHasOneOwnerAcrossReload(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 3, 2));
    c.getWorld().setBlockState(pos, Blocks.CHEST.getDefaultState(), 3);
    ((ChestBlockEntity) c.getWorld().getBlockEntity(pos))
        .setStack(0, new ItemStack(Items.DIAMOND, 7));
    var flying = FlyingBlockEntity.capture(c.getWorld(), pos, Vec3d.ZERO, 100);
    check(
        c,
        flying != null && c.getWorld().getBlockState(pos).isAir(),
        "Capture must transfer chest out of world");
    check(
        c,
        c.getWorld()
            .getEntitiesByClass(
                ItemEntity.class, new Box(pos).expand(2), e -> e.getStack().isOf(Items.DIAMOND))
            .isEmpty(),
        "Capture must not scatter a second inventory");
    NbtCompound saved = new NbtCompound();
    flying.writeNbt(saved);
    flying.discard();
    var restored = new FlyingBlockEntity(SimulationContent.FLYING_BLOCK, c.getWorld());
    restored.readNbt(saved);
    c.getWorld().spawnEntity(restored);
    check(c, restored.remainingLiftTicks() == 100, "Custom lift state must survive reload");
    restored.settle();
    restored.settle();
    check(
        c,
        c.getWorld().getBlockState(pos).isOf(Blocks.CHEST),
        "Captured chest must restore its original block");
    var chest = (ChestBlockEntity) c.getWorld().getBlockEntity(pos);
    check(
        c,
        chest.getStack(0).isOf(Items.DIAMOND) && chest.getStack(0).getCount() == 7,
        "Restoration must preserve exactly seven diamonds");
    check(
        c,
        c.getWorld()
            .getEntitiesByClass(ItemEntity.class, new Box(pos).expand(2), e -> true)
            .isEmpty(),
        "Repeated settlement must not duplicate drops");
    chest.clear();
    c.complete();
  }

  public static void raysResumeAndRespectShielding(TestContext c) {
    BlockPos center = c.getAbsolutePos(new BlockPos(2, 4, 2));
    for (int y = -3; y <= 3; y++)
      for (int z = -3; z <= 3; z++)
        c.getWorld().setBlockState(center.add(1, y, z), Blocks.BEDROCK.getDefaultState(), 2);
    BlockPos exposed = center.west(), shielded = center.east(2);
    c.getWorld().setBlockState(exposed, Blocks.STONE.getDefaultState(), 2);
    c.getWorld().setBlockState(shielded, Blocks.STONE.getDefaultState(), 2);
    RayWork original = new RayWork(false, 3, 80);
    check(
        c,
        !original.tick(c.getWorld(), Vec3d.ofCenter(center), null, 31),
        "Ray work must pause at its budget");
    RayWork restored = RayWork.read(original.write());
    boolean completed = false;
    for (int tick = 0; tick < 200 && !completed; tick++)
      completed = restored.tick(c.getWorld(), Vec3d.ofCenter(center), null, 512);
    check(c, completed, "Restored ray work must complete");
    check(c, c.getWorld().getBlockState(exposed).isAir(), "Unshielded stone must be destroyed");
    check(
        c,
        c.getWorld().getBlockState(shielded).isOf(Blocks.STONE),
        "A bedrock shield must stop ray energy reaching stone behind it");
    check(
        c,
        c.getWorld().getBlockState(center.east()).isOf(Blocks.BEDROCK),
        "Unbreakable shield must survive");
    c.complete();
  }

  public static void singularityPersistsAndConsumesCapturedBlocks(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 3, 2));
    c.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
    var captured = FlyingBlockEntity.capture(c.getWorld(), pos, Vec3d.ZERO, 50);
    var singularity = new RedmatterEntity(SimulationContent.REDMATTER, c.getWorld());
    singularity.refreshPositionAndAngles(captured.getX(), captured.getY(), captured.getZ(), 0, 0);
    singularity.setSize(2);
    c.getWorld().spawnEntity(singularity);
    singularity.tick();
    check(
        c,
        captured.isRemoved() && captured.completed(),
        "Core contact must consume conserved flying blocks once");
    NbtCompound saved = new NbtCompound();
    singularity.writeNbt(saved);
    int lived = singularity.livedTicks();
    float size = singularity.size();
    singularity.discard();
    var restored = new RedmatterEntity(SimulationContent.REDMATTER, c.getWorld());
    restored.readNbt(saved);
    check(
        c,
        restored.livedTicks() == lived && Math.abs(restored.size() - size) < .0001,
        "Singularity lifetime and fed size must persist");
    restored.discard();
    c.complete();
  }

  public static void gasCannotPassSolidEnclosure(TestContext c) {
    BlockPos center = c.getAbsolutePos(new BlockPos(2, 3, 2));
    for (Direction direction : Direction.values())
      c.getWorld().setBlockState(center.offset(direction), Blocks.STONE.getDefaultState(), 2);
    GasWork gas = new GasWork(center);
    for (int tick = 0; tick < 10; tick++) gas.tick(c.getWorld(), Vec3d.ofCenter(center), 5, 512);
    check(c, gas.contains(center), "Gas seed must remain present");
    check(c, !gas.contains(center.east(2)), "Solid enclosure must prevent diffusion to exterior");
    GasWork restored = GasWork.read(gas.write(), center);
    check(
        c,
        !restored.contains(center.east(2)),
        "Saved gas bounds must preserve collision filtering");
    c.complete();
  }
}
