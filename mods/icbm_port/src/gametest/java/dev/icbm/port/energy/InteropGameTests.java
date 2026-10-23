package dev.icbm.port.energy;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.defense.DefenseContent;
import dev.icbm.port.defense.DefenseMachineBlock;
import dev.icbm.port.defense.DefenseMachineBlockEntity;
import dev.icbm.port.launcher.LauncherBlockEntity;
import dev.icbm.port.launcher.LauncherContent;
import dev.icbm.port.payload.PayloadConfig;
import dev.icbm.port.payload.Payloads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import team.reborn.energy.api.EnergyStorage;

/**
 * Interop through the public Fabric lookups on placed blocks, as another mod would see them:
 * insert-only machine energy, EMP draining Team Reborn Energy devices, and SAM ammunition loading.
 */
public final class InteropGameTests implements FabricGameTest {
  /** EMP drains are instantaneous; a private batch keeps other tests' EMPs from overlapping. */
  private static final String EMP_BATCH = "icbmEmpEnergy";

  private static final long CHARGE = 50_000;

  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  private static long insert(EnergyStorage storage, long amount, boolean commit) {
    try (Transaction tx = Transaction.openOuter()) {
      long moved = storage.insert(amount, tx);
      if (commit) tx.commit();
      return moved;
    }
  }

  private static long extract(EnergyStorage storage, long amount) {
    try (Transaction tx = Transaction.openOuter()) {
      long moved = storage.extract(amount, tx);
      tx.commit();
      return moved;
    }
  }

  /** Other mods may charge a machine through any side but can never take its energy. */
  private static void checkInsertOnly(
      TestContext c, BlockPos pos, EnergyBuffer buffer, String name) {
    for (Direction side : new Direction[] {null, Direction.UP, Direction.NORTH}) {
      EnergyStorage storage = EnergyStorage.SIDED.find(c.getWorld(), pos, side);
      check(c, storage != null, name + " must expose EnergyStorage.SIDED on side " + side);
      check(c, storage.supportsInsertion(), name + " must accept energy from other mods");
      check(c, !storage.supportsExtraction(), name + " must not advertise extraction");
    }
    EnergyStorage storage = EnergyStorage.SIDED.find(c.getWorld(), pos, Direction.UP);
    long before = buffer.getAmount();
    check(c, insert(storage, 1000, false) == 1000, name + " must accept a simulated insert");
    check(c, buffer.getAmount() == before, name + " aborted insert must not change energy");
    check(c, insert(storage, 1000, true) == 1000, name + " must accept a committed insert");
    check(c, buffer.getAmount() == before + 1000, name + " must store inserted energy");
    check(c, extract(storage, Long.MAX_VALUE) == 0, name + " must refuse extraction");
    check(c, storage.getAmount() == before + 1000, name + " refused extraction must not drain");
    check(c, buffer.consume(1000), name + " must still pay its own costs from the buffer");
    check(c, buffer.getAmount() == before, name + " internal consumption must spend energy");
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void launcherEnergyLookupIsInsertOnly(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(1, 2, 1));
    c.getWorld().setBlockState(pos, LauncherContent.BASE_TIER_1.getDefaultState());
    var launcher = (LauncherBlockEntity) c.getWorld().getBlockEntity(pos);
    checkInsertOnly(c, pos, launcher.energyBuffer(), "Launcher");

    // Connected launchers still share energy internally.
    BlockPos donorPos = pos.east();
    c.getWorld().setBlockState(donorPos, LauncherContent.BASE_TIER_1.getDefaultState());
    var donor = (LauncherBlockEntity) c.getWorld().getBlockEntity(donorPos);
    donor.energyBuffer().setAmount(1200);
    try (Transaction tx = Transaction.openOuter()) {
      check(
          c,
          donor.energyBuffer().extract(600, tx) == 600,
          "ICBM's own launcher sharing must still extract from the buffer");
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void defenseMachineEnergyLookupIsInsertOnly(TestContext c) {
    Block[] machines = {
      DefenseContent.RADAR_STATION, DefenseContent.EMP_TOWER, DefenseContent.SAM_LAUNCHER
    };
    for (int i = 0; i < machines.length; i++) {
      BlockPos pos = c.getAbsolutePos(new BlockPos(1 + 2 * i, 2, 1));
      c.getWorld().setBlockState(pos, machines[i].getDefaultState());
      var machine = (DefenseMachineBlockEntity) c.getWorld().getBlockEntity(pos);
      checkInsertOnly(c, pos, machine.energyBuffer(), machine.kind().name());
    }
    c.complete();
  }

  /** Places a test battery block holding {@link #CHARGE} and returns its storage. */
  private static EnergyStorage battery(TestContext c, BlockPos relative) {
    BlockPos pos = c.getAbsolutePos(relative);
    c.getWorld().setBlockState(pos, TestEnergyFixtures.BATTERY_BLOCK.getDefaultState());
    var battery = (TestEnergyFixtures.BatteryBlockEntity) c.getWorld().getBlockEntity(pos);
    battery.energy.amount = CHARGE;
    return EnergyStorage.SIDED.find(c.getWorld(), pos, Direction.UP);
  }

  /** Spawns a chest minecart holding a charged test battery item. */
  private static ChestMinecartEntity cart(TestContext c, BlockPos relative) {
    Vec3d at = Vec3d.ofCenter(c.getAbsolutePos(relative));
    var cart = new ChestMinecartEntity(c.getWorld(), at.x, at.y, at.z);
    cart.setStack(0, TestEnergyFixtures.chargedItem(CHARGE));
    c.getWorld().spawnEntity(cart);
    return cart;
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = EMP_BATCH)
  public void empDrainsEnergyDevicesInsideRadiusOnly(TestContext c) {
    check(
        c,
        Payloads.EMP_HANDLERS.contains(EmpEnergyDrain.PAYLOAD_HANDLER),
        "EMP payloads must run the energy drain handler");
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(1, 2, 1)));
    EnergyStorage near = battery(c, new BlockPos(2, 2, 1));
    EnergyStorage far = battery(c, new BlockPos(7, 2, 7));
    ChestMinecartEntity nearCart = cart(c, new BlockPos(1, 3, 2));
    ChestMinecartEntity farCart = cart(c, new BlockPos(7, 3, 6));
    BlockPos samPos = c.getAbsolutePos(new BlockPos(1, 2, 2));
    c.getWorld().setBlockState(samPos, DefenseContent.SAM_LAUNCHER.getDefaultState());
    var sam = (DefenseMachineBlockEntity) c.getWorld().getBlockEntity(samPos);
    sam.energyBuffer().setAmount(CHARGE);

    var result =
        EmpEnergyDrain.drain(c.getWorld(), center, 4, PayloadConfig.EmpEnergyDrain.DEFAULT);

    check(c, near.getAmount() == 0, "EMP must empty a rate-limited energy block in range");
    check(
        c,
        TestEnergyFixtures.stored(nearCart.getStack(0)) == 0,
        "EMP must empty energy items in entity inventories in range");
    check(c, far.getAmount() == CHARGE, "EMP must not touch energy blocks outside its radius");
    check(
        c,
        TestEnergyFixtures.stored(farCart.getStack(0)) == CHARGE,
        "EMP must not touch energy items outside its radius");
    check(
        c,
        sam.energyBuffer().getAmount() == CHARGE,
        "ICBM machines are insert-only to the drain; EMP fields disable them instead");
    check(
        c,
        result.blocks() == 1 && result.items() == 1 && result.energy() == 2 * CHARGE,
        "EMP must report exactly what it drained: " + result);
    nearCart.discard();
    farCart.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = EMP_BATCH)
  public void empLeavesEnergyDevicesAloneWhenDrainDisabled(TestContext c) throws IOException {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(1, 2, 1)));
    EnergyStorage near = battery(c, new BlockPos(2, 2, 1));
    ChestMinecartEntity nearCart = cart(c, new BlockPos(1, 3, 2));
    // Turn the drain off through the real config file and operator reload, then restore it.
    Path config =
        FabricLoader.getInstance().getConfigDir().resolve("icbm_port").resolve("payloads.json");
    String original = Files.readString(config);
    EmpEnergyDrain.Result result;
    try {
      Files.writeString(config, "{\"" + PayloadConfig.EMP_ENERGY_DRAIN + "\":{\"enabled\":false}}");
      PayloadConfig.reload();
      check(c, !PayloadConfig.empEnergyDrain().enabled(), "Reload must apply the drain toggle");
      result = EmpEnergyDrain.drain(c.getWorld(), center, 4, PayloadConfig.empEnergyDrain());
    } finally {
      Files.writeString(config, original);
      PayloadConfig.reload();
    }
    check(c, result.equals(EmpEnergyDrain.Result.NONE), "Disabled drain must do nothing");
    check(c, near.getAmount() == CHARGE, "Disabled drain must leave energy blocks charged");
    check(
        c,
        TestEnergyFixtures.stored(nearCart.getStack(0)) == CHARGE,
        "Disabled drain must leave energy items charged");
    check(c, PayloadConfig.empEnergyDrain().enabled(), "Restored config must re-enable the drain");
    nearCart.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void hoppersLoadSamAmmoAndRejectOtherItems(TestContext c) {
    BlockPos samPos = new BlockPos(1, 1, 1), rejectPos = new BlockPos(4, 1, 1);
    var downHopper = Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING, Direction.DOWN);
    c.setBlockState(samPos, DefenseContent.SAM_LAUNCHER.getDefaultState());
    c.setBlockState(rejectPos, DefenseContent.SAM_LAUNCHER.getDefaultState());
    c.setBlockState(samPos.up(), downHopper);
    c.setBlockState(rejectPos.up(), downHopper);
    var ammoHopper = (HopperBlockEntity) c.getBlockEntity(samPos.up());
    var stoneHopper = (HopperBlockEntity) c.getBlockEntity(rejectPos.up());
    ammoHopper.setStack(0, new ItemStack(DefenseContent.SAM_MISSILE, 3));
    stoneHopper.setStack(0, new ItemStack(Items.STONE, 3));
    // Hoppers move one item every 8 ticks.
    c.runAtTick(
        60,
        () -> {
          check(
              c,
              c.getBlockState(samPos).get(DefenseMachineBlock.POWER) == 3,
              "A vanilla hopper must load sam_missile items into the SAM magazine");
          check(c, ammoHopper.isEmpty(), "Loaded missiles must leave the hopper");
          check(
              c,
              c.getBlockState(rejectPos).get(DefenseMachineBlock.POWER) == 0,
              "Other items must not load the SAM magazine");
          check(
              c,
              stoneHopper.getStack(0).getCount() == 3,
              "Rejected items must stay in the hopper");
          c.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void samItemLookupIsInsertOnlyAndCapped(TestContext c) {
    BlockPos sam = c.getAbsolutePos(new BlockPos(1, 2, 1));
    BlockPos radar = c.getAbsolutePos(new BlockPos(3, 2, 1));
    c.getWorld().setBlockState(sam, DefenseContent.SAM_LAUNCHER.getDefaultState());
    c.getWorld().setBlockState(radar, DefenseContent.RADAR_STATION.getDefaultState());
    var storage = ItemStorage.SIDED.find(c.getWorld(), sam, Direction.UP);
    check(c, storage != null, "SAM station must expose ItemStorage.SIDED");
    ItemVariant missile = ItemVariant.of(DefenseContent.SAM_MISSILE);
    try (Transaction tx = Transaction.openOuter()) {
      check(c, storage.insert(ItemVariant.of(Items.STONE), 1, tx) == 0, "Must reject other items");
      check(c, storage.insert(missile, 4, tx) == 4, "Must accept sam_missile items");
    }
    check(
        c,
        c.getWorld().getBlockState(sam).get(DefenseMachineBlock.POWER) == 0,
        "Aborted insertion must not load ammunition");
    try (Transaction tx = Transaction.openOuter()) {
      check(c, storage.insert(missile, 10, tx) == 10, "Must accept a partial magazine");
      check(
          c,
          storage.insert(missile, 64, tx) == DefenseMachineBlock.MAX_SAM_AMMO - 10,
          "Must stop at the magazine size within one transaction");
      tx.commit();
    }
    check(
        c,
        c.getWorld().getBlockState(sam).get(DefenseMachineBlock.POWER)
            == DefenseMachineBlock.MAX_SAM_AMMO,
        "Committed insertion must fill the magazine counter");
    check(c, simulateInsert(storage, missile) == 0, "A full magazine must refuse");
    try (Transaction tx = Transaction.openOuter()) {
      check(
          c, storage.extract(missile, 1, tx) == 0, "Loaded ammunition must not be extractable");
    }
    var radarStorage = ItemStorage.SIDED.find(c.getWorld(), radar, Direction.UP);
    check(
        c,
        simulateInsert(radarStorage, missile) == 0,
        "Only SAM stations accept sam_missile items");
    ItemVariant battery = ItemVariant.of(Registry.ITEM.get(IcbmPort.id("battery")));
    check(
        c,
        simulateInsert(radarStorage, battery) == 1 && simulateInsert(storage, battery) == 1,
        "The battery slot must stay reachable through the item lookup");
    c.complete();
  }

  private static long simulateInsert(Storage<ItemVariant> storage, ItemVariant variant) {
    try (Transaction tx = Transaction.openOuter()) {
      return storage.insert(variant, 1, tx);
    }
  }
}
