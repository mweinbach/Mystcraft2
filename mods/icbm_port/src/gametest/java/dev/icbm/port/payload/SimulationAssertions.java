package dev.icbm.port.payload;

import dev.icbm.port.simulation.*;
import java.util.List;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MovementType;
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

  public static void redmatterSweepsMovesButNotTeleports(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 5, 2)));
    var core = new RedmatterEntity(SimulationContent.REDMATTER, c.getWorld());
    core.refreshPositionAndAngles(center.x, center.y, center.z, 0, 0);
    c.getWorld().spawnEntity(core);
    var moved = stillItem(c, center.add(0, 0, -2.5), Items.DIAMOND);
    moved.move(MovementType.SELF, new Vec3d(0, 0, 5));
    // A short jump across the core that leaves prevX/Y/Z behind, as direct setPosition callers do.
    var teleported = stillItem(c, center.add(-2.5, 0, 0), Items.EMERALD);
    teleported.move(MovementType.SELF, new Vec3d(.1, 0, 0));
    teleported.setPosition(center.add(2.5, 0, 0));
    core.tick();
    check(c, moved.isRemoved(), "A physical step through the core must be consumed");
    check(c, !teleported.isRemoved(), "A teleport across the core must not count as passing it");
    teleported.discard();
    core.discard();
    c.complete();
  }

  private static ItemEntity stillItem(TestContext c, Vec3d pos, Item item) {
    var entity = new ItemEntity(c.getWorld(), pos.x, pos.y, pos.z, new ItemStack(item), 0, 0, 0);
    entity.setNoGravity(true);
    entity.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
    c.getWorld().spawnEntity(entity);
    return entity;
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

  public static void placedRedmatterReachesDistantTerrain(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 44, 2));
    var block = dev.icbm.port.IcbmPort.EXPLOSIVE_BLOCKS.get(PayloadType.REDMATTER);
    c.getWorld().setBlockState(pos.down(), Blocks.BEDROCK.getDefaultState(), 2);
    c.getWorld().setBlockState(pos, block.getDefaultState(), 2);
    BlockPos target = pos.east(6);
    c.getWorld().setBlockState(target, Blocks.STONE.getDefaultState(), 2);
    // Fixed drop-style velocities whose sideways drift carries each item through the core between
    // ticks; the core must still consume them instead of slinging them out of its field.
    var items =
        List.of(
            new ItemEntity(
                c.getWorld(),
                pos.getX() + 4.5,
                pos.getY() + .4,
                pos.getZ() + .5,
                new ItemStack(Items.DIAMOND),
                .044,
                .2,
                .001),
            new ItemEntity(
                c.getWorld(),
                pos.getX() + 4.5,
                pos.getY() + .4,
                pos.getZ() + .5,
                new ItemStack(Items.EMERALD),
                .075,
                .2,
                .07));
    for (ItemEntity item : items) {
      item.setNoGravity(true);
      c.getWorld().spawnEntity(item);
    }
    check(c, block.prime(c.getWorld(), pos, 1), "Placed redmatter block must prime");
    c.getWorld().removeBlock(pos, false);
    c.runAtTick(
        200,
        () -> {
          try {
            var cores =
                c.getWorld()
                    .getEntitiesByType(
                        SimulationContent.REDMATTER, new Box(pos).expand(10), e -> e.isAlive());
            check(c, cores.size() == 1, "Primed redmatter must create one persistent core");
            RedmatterEntity core = cores.get(0);
            check(
                c,
                c.getWorld().isAir(target),
                "Redmatter must find terrain six blocks away after its inner shells are empty");
            check(
                c,
                core.capturedBlocks() > 0 && core.consumedBlocks() > 0,
                "Captured terrain must actually reach and feed the core");
            check(
                c,
                items.stream().allMatch(ItemEntity::isRemoved),
                "The gravitational field must pull and consume nearby dropped items");
            NbtCompound saved = new NbtCompound();
            core.writeNbt(saved);
            var restored = new RedmatterEntity(SimulationContent.REDMATTER, c.getWorld());
            restored.readNbt(saved);
            check(
                c,
                restored.searchRadius() == core.searchRadius()
                    && restored.consumedBlocks() == core.consumedBlocks(),
                "Scan progress and consumption counters must survive reload");
            restored.discard();
          } finally {
            Payloads.removeNear(c.getWorld(), Vec3d.ofCenter(pos), 12);
            items.forEach(ItemEntity::discard);
            c.getWorld().removeBlock(pos.down(), false);
            c.getWorld().removeBlock(target, false);
          }
          c.complete();
        });
  }

  public static void antimatterNeutralizesWithoutEntityPulse(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 4, 2));
    Vec3d center = Vec3d.ofCenter(pos);
    check(
        c,
        Payloads.detonateScaled(c.getWorld(), center, PayloadType.REDMATTER, null, 2),
        "Redmatter detonation must be accepted");
    var pig = net.minecraft.entity.EntityType.PIG.create(c.getWorld());
    pig.setNoGravity(true);
    pig.setAiDisabled(true);
    pig.refreshPositionAndAngles(center.x, center.y, center.z, 0, 0);
    c.getWorld().spawnEntity(pig);
    float health = pig.getHealth();
    check(
        c,
        Payloads.detonateScaled(c.getWorld(), center, PayloadType.ANTIMATTER, null, 2),
        "Antimatter counter must be accepted");
    check(
        c,
        c.getWorld()
            .getEntitiesByType(
                SimulationContent.REDMATTER, new Box(pos).expand(2), e -> e.isAlive())
            .isEmpty(),
        "Antimatter must neutralize the singularity");
    c.runAtTick(
        36,
        () -> {
          check(
              c,
              pig.isAlive() && pig.getHealth() == health,
              "Neutralizing redmatter must suppress antimatter's entity damage pulse, as upstream"
                  + " defaults require");
          Payloads.removeNear(c.getWorld(), center, 2);
          pig.discard();
          c.complete();
        });
  }

  public static void restoredCaptureWaitsForItsCore(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 84, 2));
    var originalCore = new RedmatterEntity(SimulationContent.REDMATTER, c.getWorld());
    originalCore.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
    NbtCompound coreSave = new NbtCompound();
    originalCore.writeNbt(coreSave);
    originalCore.discard();
    BlockPos cargoPos = pos.east(4);
    c.getWorld().setBlockState(cargoPos, Blocks.STONE.getDefaultState(), 2);
    var original = FlyingBlockEntity.capture(c.getWorld(), cargoPos, Vec3d.ZERO, 0);
    original.bindToSingularity(originalCore.getUuid());
    NbtCompound cargoSave = new NbtCompound();
    original.writeNbt(cargoSave);
    original.discard();
    var restored = new FlyingBlockEntity(SimulationContent.FLYING_BLOCK, c.getWorld());
    restored.readNbt(cargoSave);
    c.getWorld().spawnEntity(restored);
    Vec3d held = restored.getPos();
    for (int tick = 0; tick < 5; tick++) restored.tick();
    NbtCompound waiting = new NbtCompound();
    restored.writeNbt(waiting);
    check(
        c,
        waiting.containsUuid("singularity") && restored.getPos().squaredDistanceTo(held) < .000001,
        "Cargo loaded before its core must preserve binding and hold position");
    var core = new RedmatterEntity(SimulationContent.REDMATTER, c.getWorld());
    core.readNbt(coreSave);
    c.getWorld().spawnEntity(core);
    for (int tick = 0; tick < 40 && !restored.isRemoved(); tick++) {
      restored.tick();
      core.tick();
    }
    check(
        c,
        restored.isRemoved() && restored.completed() && core.consumedBlocks() == 1,
        "Late-loaded core must resume homing and consume exactly one captured block");
    check(c, c.getWorld().isAir(cargoPos), "Consumed cargo must not recreate its source block");
    core.discard();

    // A missing owner must not leave immortal hovering inventory after the grace.
    c.getWorld().setBlockState(cargoPos, Blocks.CHEST.getDefaultState(), 2);
    ((ChestBlockEntity) c.getWorld().getBlockEntity(cargoPos))
        .setStack(0, new ItemStack(Items.DIAMOND, 3));
    var missing = FlyingBlockEntity.capture(c.getWorld(), cargoPos, Vec3d.ZERO, 0);
    missing.bindToSingularity(java.util.UUID.randomUUID());
    NbtCompound missingSave = new NbtCompound();
    missing.writeNbt(missingSave);
    missing.discard();
    var fallback = new FlyingBlockEntity(SimulationContent.FLYING_BLOCK, c.getWorld());
    fallback.readNbt(missingSave);
    c.getWorld().spawnEntity(fallback);
    for (int tick = 0; tick < 100; tick++) fallback.tick();
    NbtCompound before = new NbtCompound();
    fallback.writeNbt(before);
    check(
        c,
        before.containsUuid("singularity"),
        "Reload grace must last exactly100 absent-core ticks");
    fallback.tick();
    NbtCompound after = new NbtCompound();
    fallback.writeNbt(after);
    check(
        c,
        !after.containsUuid("singularity"),
        "A core that never loads must release cargo after finite grace");
    fallback.settle();
    fallback.settle();
    var chest = (ChestBlockEntity) c.getWorld().getBlockEntity(cargoPos);
    check(
        c,
        chest != null && chest.getStack(0).isOf(Items.DIAMOND) && chest.getStack(0).getCount() == 3,
        "Fallback must restore one complete inventory without duplication");
    chest.clear();
    c.getWorld().removeBlock(cargoPos, false);

    // Grace is load-only: losing an already observed core releases immediately.
    var known = new RedmatterEntity(SimulationContent.REDMATTER, c.getWorld());
    known.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
    c.getWorld().spawnEntity(known);
    c.getWorld().setBlockState(cargoPos, Blocks.STONE.getDefaultState(), 2);
    var fresh = FlyingBlockEntity.capture(c.getWorld(), cargoPos, Vec3d.ZERO, 0);
    fresh.bindToSingularity(known.getUuid());
    fresh.tick();
    known.discard();
    fresh.tick();
    NbtCompound released = new NbtCompound();
    fresh.writeNbt(released);
    check(
        c,
        !released.containsUuid("singularity"),
        "Known core destruction must release bound cargo immediately");
    fresh.consume();
    c.complete();
  }
}
