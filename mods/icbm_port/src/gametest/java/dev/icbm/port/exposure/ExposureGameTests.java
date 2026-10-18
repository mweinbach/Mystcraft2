package dev.icbm.port.exposure;

import dev.icbm.port.payload.PayloadType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

public final class ExposureGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void toxicityPersistsAndAntidoteClears(TestContext c) {
    var pig = EntityType.COW.create(c.getWorld());
    ExposureContent.expose(pig, PayloadType.CHEMICAL);
    ExposureContent.expose(pig, PayloadType.CONTAGIOUS);
    ExposureState state = ExposureState.get(c.getWorld());
    ExposureState restored = ExposureState.read(state.writeNbt(new NbtCompound()));
    var dose = restored.dose(pig.getUuid());
    check(
        c,
        dose.chemicalHits == 1 && dose.viralHits == 1 && dose.infectiousTicks == 900,
        "Exposure must round-trip with source counters and infection lifetime");
    check(
        c,
        pig.hasStatusEffect(StatusEffects.NAUSEA) && pig.hasStatusEffect(StatusEffects.BLINDNESS),
        "Chemical and viral effects must remain distinct");
    ExposureContent.clear(pig);
    check(c, !state.doses.containsKey(pig.getUuid()), "Treatment clears persistent dose");
    pig.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void armorNeedsHelmetAndStopsGas(TestContext c) {
    var pig = EntityType.COW.create(c.getWorld());
    ExposureConfig old = ExposureContent.config;
    try {
      ExposureContent.config = new ExposureConfig();
      ExposureContent.config.armorRatings.put("minecraft:leather_chestplate", 1f);
      pig.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
      check(
          c,
          ExposureContent.protection(pig) == 0,
          "A configured chestplate alone fails required helmet gate");
      pig.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
      check(c, ExposureContent.protection(pig) == 1, "Helmet allows configured full protection");
      ExposureContent.expose(pig, PayloadType.CHEMICAL);
      check(c, !pig.hasStatusEffect(StatusEffects.POISON), "Full protection prevents gas effects");
    } finally {
      ExposureContent.config = old;
      pig.discard();
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void radioactiveStoneDecaysToHarmlessStone(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(1, 2, 1));
    ExposureConfig old = ExposureContent.config;
    try {
      ExposureContent.config = new ExposureConfig();
      ExposureContent.config.decayChance = 1;
      ExposureContent.config.effectChance = 0;
      c.getWorld().setBlockState(pos, ExposureContent.RADIOACTIVE_STONE.getDefaultState());
      ExposureContent.RADIOACTIVE_STONE.decay(c.getWorld(), pos, c.getWorld().random);
      check(
          c,
          c.getWorld().getBlockState(pos).isOf(Blocks.STONE),
          "Radioactive stone must decay into source clean state");
    } finally {
      ExposureContent.config = old;
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void falloutCursorSurvivesSave(TestContext c) {
    FalloutState state = new FalloutState();
    BlockPos pos = c.getAbsolutePos(new BlockPos(1, 2, 1));
    state.jobs.add(new FalloutState.Job(pos, 12, 4096));
    FalloutState restored = FalloutState.read(state.writeNbt(new NbtCompound()));
    check(
        c,
        restored.jobs.size() == 1
            && restored.jobs.peek().cursor() == 4096
            && restored.jobs.peek().origin().equals(pos),
        "Fallout work must retain origin/radius/cursor across save");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 140)
  public void infectionSpreadsToNearbyLivingTarget(TestContext c) {
    var source = EntityType.COW.create(c.getWorld());
    var target = EntityType.COW.create(c.getWorld());
    BlockPos pos = c.getAbsolutePos(new BlockPos(1, 2, 1));
    source.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
    target.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ(), 0, 0);
    source.setAiDisabled(true);
    target.setAiDisabled(true);
    c.getWorld().spawnEntity(source);
    c.getWorld().spawnEntity(target);
    ExposureContent.expose(source, PayloadType.CONTAGIOUS);
    c.runAtTick(
        105,
        () -> {
          var dose = ExposureState.get(c.getWorld()).doses.get(target.getUuid());
          check(
              c,
              dose != null && dose.infectiousTicks > 0,
              "Unprotected nearby target must acquire persistent viral exposure");
          ExposureContent.clear(source);
          ExposureContent.clear(target);
          source.discard();
          target.discard();
          c.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void doseAdmissionIsBoundedAndUnloadedRecordsSurvive(TestContext c) {
    ExposureState state = new ExposureState();
    java.util.UUID first = new java.util.UUID(0, 0);
    state.dose(first).chemicalHits = 21;
    for (int i = 1; i < ExposureState.MAX_ENTITIES; i++) state.dose(new java.util.UUID(0, i));
    java.util.UUID overflow = new java.util.UUID(1, 0);
    state.dose(overflow).viralHits = 7;
    check(
        c,
        state.doses.size() == ExposureState.MAX_ENTITIES && state.find(overflow) == null,
        "Full state must not admit or evict UUIDs");
    state.onRemoval(first, net.minecraft.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
    check(
        c,
        state.find(first) != null && state.find(first).chemicalHits == 21,
        "Chunk unloading preserves admitted exposure");
    state.onRemoval(first, net.minecraft.entity.Entity.RemovalReason.CHANGED_DIMENSION);
    check(c, state.find(first) != null, "Dimension change preserves global UUID exposure");
    state.onRemoval(first, net.minecraft.entity.Entity.RemovalReason.KILLED);
    check(c, state.find(first) == null, "Permanent death frees its exposure record");
    state.dose(overflow).viralHits = 7;
    check(
        c,
        state.find(overflow) != null && state.find(overflow).viralHits == 7,
        "Freed capacity admits new persistent exposure");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
  public void discardedLivingEntityReleasesPersistentDose(TestContext c) {
    var cow = EntityType.COW.create(c.getWorld());
    BlockPos pos = c.getAbsolutePos(new BlockPos(1, 2, 1));
    cow.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
    c.getWorld().spawnEntity(cow);
    ExposureContent.expose(cow, PayloadType.CHEMICAL);
    ExposureState state = ExposureState.get(c.getWorld());
    check(c, state.find(cow.getUuid()) != null, "Test entity acquires persistent exposure");
    cow.discard();
    c.runAtTick(
        2,
        () -> {
          check(
              c,
              state.find(cow.getUuid()) == null,
              "Discard unload callback must clear persistent UUID");
          c.complete();
        });
  }
}
