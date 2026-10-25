package dev.icbm.port.defense;

import dev.icbm.port.exposure.ExposureContent;
import dev.icbm.port.exposure.RadioactiveBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.AbstractButtonBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

/** Strength, harvest, drops and behaviour of upstream ICBM-Classic defensive/ambient blocks. */
public class DefenseBlockParityGameTests implements FabricGameTest {
  private void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  private void strength(
      TestContext context, BlockPos pos, Block block, float hardness, float resistance) {
    BlockState state = block.getDefaultState();
    check(
        context,
        Math.abs(state.getHardness(context.getWorld(), pos) - hardness) < 1e-4
            && Math.abs(block.getBlastResistance() - resistance) < 1e-4,
        block + " must have hardness " + hardness + " and resistance " + resistance);
  }

  private void dropsItself(TestContext context, BlockPos pos, Block block, ItemStack tool) {
    var drops =
        Block.getDroppedStacks(
            block.getDefaultState(), context.getWorld(), pos, null, null, tool);
    check(
        context,
        drops.size() == 1 && drops.get(0).isOf(block.asItem()) && drops.get(0).getCount() == 1,
        block + " must drop exactly itself");
  }

  private PigEntity pig(TestContext context) {
    PigEntity pig = EntityType.PIG.create(context.getWorld());
    check(context, pig != null, "Test pig must exist");
    return pig;
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void blockStrengthMatchesUpstream(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(1, 1, 1));
    strength(context, pos, DefenseContent.CONCRETE, 10, 28);
    strength(context, pos, DefenseContent.COMPACT_CONCRETE, 10, 280);
    strength(context, pos, DefenseContent.REINFORCED_CONCRETE, 10, 2800);
    strength(context, pos, DefenseContent.REINFORCED_GLASS, 10, 28.8F);
    strength(context, pos, DefenseContent.SPIKES, 1, 1);
    strength(context, pos, DefenseContent.POISON_SPIKES, 1, 1);
    strength(context, pos, DefenseContent.FIRE_SPIKES, 1, 1);
    strength(context, pos, DefenseContent.GLASS_PRESSURE_PLATE, 0.3F, 0.6F);
    strength(context, pos, DefenseContent.GLASS_BUTTON, 0.5F, 0.5F);
    strength(context, pos, ExposureContent.RADIOACTIVE_DIRT, 0.5F, 0.5F);
    strength(context, pos, ExposureContent.RADIOACTIVE_STONE, 1.5F, 6);
    check(
        context,
        DefenseContent.GLASS_PRESSURE_PLATE.getDefaultState().getSoundGroup() == BlockSoundGroup.GLASS
            && DefenseContent.GLASS_BUTTON.getDefaultState().getSoundGroup() == BlockSoundGroup.GLASS
            && DefenseContent.REINFORCED_GLASS.getDefaultState().getSoundGroup()
                == BlockSoundGroup.STONE,
        "Glass plate/button use glass sounds; reinforced glass keeps the stone sound");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void harvestRulesAndLootMatchUpstream(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(1, 1, 1));
    ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
    Block[] pickaxeOnly = {
      DefenseContent.CONCRETE,
      DefenseContent.COMPACT_CONCRETE,
      DefenseContent.REINFORCED_CONCRETE,
      DefenseContent.SPIKES,
      DefenseContent.POISON_SPIKES,
      DefenseContent.FIRE_SPIKES,
      ExposureContent.RADIOACTIVE_DIRT,
      ExposureContent.RADIOACTIVE_STONE
    };
    for (Block block : pickaxeOnly) {
      BlockState state = block.getDefaultState();
      check(
          context,
          state.isToolRequired() && pickaxe.isSuitableFor(state),
          block + " must need a pickaxe to drop");
      dropsItself(context, pos, block, pickaxe);
    }
    Block[] anyTool = {
      DefenseContent.REINFORCED_GLASS, DefenseContent.GLASS_BUTTON, DefenseContent.GLASS_PRESSURE_PLATE
    };
    for (Block block : anyTool) {
      check(
          context, !block.getDefaultState().isToolRequired(), block + " must drop without a tool");
      dropsItself(context, pos, block, ItemStack.EMPTY);
    }
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void spikesUseOwnDamageSourceAndConfig(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    PigEntity plain = pig(context);
    float health = plain.getHealth();
    DefenseContent.SPIKES.onEntityCollision(
        DefenseContent.SPIKES.getDefaultState(), context.getWorld(), pos, plain);
    check(
        context,
        Math.abs(plain.getHealth() - (health - 1)) < 1e-4
            && plain.getRecentDamageSource() == SpikeBlock.DAMAGE_SOURCE
            && "icbm_port.spikes".equals(SpikeBlock.DAMAGE_SOURCE.getName()),
        "Spikes must deal one point of spike damage by default");
    DefenseBlockConfig previous = DefenseContent.blockConfig;
    try {
      DefenseBlockConfig config = new DefenseBlockConfig();
      config.fireSpikeDamage = 3;
      config.poisonSpikeDamage = 0;
      config.normalSpikeDamage = Float.NaN;
      DefenseContent.blockConfig = config;
      check(
          context,
          config.damageFor(SpikeBlock.Kind.POISON) == 1
              && config.damageFor(SpikeBlock.Kind.NORMAL) == 1
              && config.damageFor(SpikeBlock.Kind.FIRE) == 3,
          "Configured spike damage must respect the upstream one-point minimum");
      PigEntity burned = pig(context);
      float before = burned.getHealth();
      DefenseContent.FIRE_SPIKES.onEntityCollision(
          DefenseContent.FIRE_SPIKES.getDefaultState(), context.getWorld(), pos, burned);
      check(
          context,
          Math.abs(burned.getHealth() - (before - 3)) < 1e-4 && burned.isOnFire(),
          "Fire spikes must use configured damage and ignite");
    } finally {
      DefenseContent.blockConfig = previous;
    }
    check(
        context,
        DefenseContent.SPIKES
                .getDefaultState()
                .getOutlineShape(context.getWorld(), pos, ShapeContext.absent())
                .getBoundingBox()
                .maxY
            == 1,
        "Spikes keep upstream's full-block selection box");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
  public void glassPressurePlateDetectsItems(TestContext context) {
    BlockPos base = new BlockPos(2, 1, 2);
    context.setBlockState(base, Blocks.STONE);
    BlockPos plate = base.up();
    context.setBlockState(plate, DefenseContent.GLASS_PRESSURE_PLATE);
    BlockPos absolute = context.getAbsolutePos(plate);
    ItemEntity item =
        new ItemEntity(
            context.getWorld(),
            absolute.getX() + .5,
            absolute.getY() + .05,
            absolute.getZ() + .5,
            new ItemStack(Items.STICK));
    item.setVelocity(0, 0, 0);
    context.getWorld().spawnEntity(item);
    context.runAtTick(
        10,
        () -> {
          check(
              context,
              context.getBlockState(plate).get(PressurePlateBlock.POWERED),
              "Glass pressure plate must activate for any entity, including items");
          item.discard();
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60)
  public void glassButtonPulsesLikeStoneButton(TestContext context) {
    BlockPos base = new BlockPos(2, 1, 2);
    context.setBlockState(base, Blocks.STONE);
    BlockPos button = base.up();
    context.setBlockState(
        button,
        DefenseContent.GLASS_BUTTON
            .getDefaultState()
            .with(WallMountedBlock.FACE, WallMountLocation.FLOOR));
    BlockPos absolute = context.getAbsolutePos(button);
    ((AbstractButtonBlock) DefenseContent.GLASS_BUTTON)
        .powerOn(context.getWorld().getBlockState(absolute), context.getWorld(), absolute);
    check(
        context,
        context.getBlockState(button).get(AbstractButtonBlock.POWERED),
        "Pressed glass button must emit power");
    context.runAtTick(
        15,
        () ->
            check(
                context,
                context.getBlockState(button).get(AbstractButtonBlock.POWERED),
                "Glass button must stay on for the stone-button pulse"));
    context.runAtTick(
        30,
        () -> {
          check(
              context,
              !context.getBlockState(button).get(AbstractButtonBlock.POWERED),
              "Glass button must release after twenty ticks");
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void radioactiveDecayChainStartsOnRandomTick(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    var world = context.getWorld();
    int delay = ExposureContent.config.decayDelay;
    boolean randomOnly = ExposureContent.config.randomTickOnly;
    float chance = ExposureContent.config.decayChance;
    float effect = ExposureContent.config.effectChance;
    try {
      ExposureContent.config.decayDelay = 5;
      ExposureContent.config.randomTickOnly = false;
      ExposureContent.config.decayChance = 0;
      ExposureContent.config.effectChance = 1;
      world.setBlockState(pos, ExposureContent.RADIOACTIVE_STONE.getDefaultState());
      check(
          context,
          !world.getBlockTickScheduler().isQueued(pos, ExposureContent.RADIOACTIVE_STONE),
          "Placing a radioactive block must not start the decay chain (upstream waits for a random tick)");
      PigEntity victim = pig(context);
      victim.refreshPositionAndAngles(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 0, 0);
      world.spawnEntity(victim);
      world
          .getBlockState(pos)
          .randomTick(world, pos, world.random);
      check(
          context,
          world.getBlockTickScheduler().isQueued(pos, ExposureContent.RADIOACTIVE_STONE),
          "A random tick must start the scheduled decay chain");
      check(
          context,
          victim.getRecentDamageSource() == RadioactiveBlock.DAMAGE_SOURCE
              && "icbm_port.radioactive_block".equals(RadioactiveBlock.DAMAGE_SOURCE.getName()),
          "Radiation must use the radioactive-block damage source");
      victim.discard();
    } finally {
      ExposureContent.config.decayDelay = delay;
      ExposureContent.config.randomTickOnly = randomOnly;
      ExposureContent.config.decayChance = chance;
      ExposureContent.config.effectChance = effect;
    }
    world.setBlockState(pos, Blocks.AIR.getDefaultState());
    context.complete();
  }
}
