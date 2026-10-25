package dev.icbm.port;

import dev.icbm.port.block.LauncherBlock;
import dev.icbm.port.launcher.LauncherBlockEntity;
import dev.icbm.port.launcher.LauncherContent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class LauncherGameTests implements FabricGameTest {
  private LauncherBlockEntity launcher(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context
        .getWorld()
        .setBlockState(
            pos, IcbmPort.LAUNCHER.getDefaultState().with(LauncherBlock.FACING, Direction.UP));
    return (LauncherBlockEntity) context.getWorld().getBlockEntity(pos);
  }

  private void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void inventoryTargetAndEnergyPersist(TestContext context) {
    LauncherBlockEntity original = launcher(context);
    original.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    original.setStack(1, new ItemStack(Items.REDSTONE, 3));
    original.charge();
    BlockPos target = original.getPos().east(80);
    check(context, original.configure(target, true, 42), "Valid target rejected");
    NbtCompound saved = new NbtCompound();
    original.writeNbt(saved);
    LauncherBlockEntity restored =
        new LauncherBlockEntity(original.getPos(), original.getCachedState());
    restored.readNbt(saved);
    check(
        context,
        restored.getStack(0).isOf(IcbmPort.CONDENSED_MISSILE)
            && restored.getStack(1).getCount() == 2,
        "Payload and fuel must survive reload");
    check(
        context,
        restored.energy() == 2000
            && restored.frequency() == 42
            && restored.targeted()
            && restored.target().equals(target),
        "Target, mode, channel and energy must survive reload");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void invalidConfigurationIsAtomic(TestContext context) {
    LauncherBlockEntity launcher = launcher(context);
    BlockPos valid = launcher.getPos().east(40);
    launcher.configure(valid, true, 7);
    check(
        context,
        !launcher.configure(launcher.getPos().east(5000), false, 9),
        "Out of range packet must be rejected");
    check(
        context,
        !launcher.configure(valid, false, -1),
        "Negative frequency packet must be rejected");
    check(
        context,
        !launcher.configure(new BlockPos(valid.getX(), Integer.MAX_VALUE, valid.getZ()), false, 5),
        "Invalid build height must be rejected");
    check(
        context,
        launcher.target().equals(valid) && launcher.targeted() && launcher.frequency() == 7,
        "Invalid configuration must not mutate launcher");
    check(
        context,
        !LauncherContent.validAction(3, 0)
            && !LauncherContent.validAction(1, 1)
            && !LauncherContent.validAction(-1, 0),
        "Unknown packet actions must fail closed");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void depletedLauncherPreservesMissile(TestContext context) {
    LauncherBlockEntity launcher = launcher(context);
    NbtCompound saved = new NbtCompound();
    launcher.writeNbt(saved);
    saved.putInt("Energy", 0);
    launcher.readNbt(saved);
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    check(
        context,
        !IcbmPort.LAUNCHER.launch(context.getWorld(), launcher.getPos()),
        "Uncharged launcher must not fire");
    check(context, !launcher.getStack(0).isEmpty(), "Failed launch must preserve payload");
    launcher.setStack(1, new ItemStack(Items.REDSTONE));
    check(
        context, launcher.charge() && launcher.energy() == 1000, "One dust must charge one launch");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void tierFramesRangeAndCapacityDiffer(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context.getWorld().setBlockState(pos, LauncherContent.BASE_TIER_1.getDefaultState());
    check(
        context,
        !LauncherContent.validTarget(
            context.getWorld(), pos, pos.east(LauncherContent.BASE_TIER_1.range() + 1)),
        "Source range must be bounded by flight configuration");
    context.getWorld().setBlockState(pos, LauncherContent.BASE_TIER_3.getDefaultState());
    check(
        context,
        LauncherContent.validTarget(context.getWorld(), pos, pos.east(1500)),
        "All source tiers must support the same configured range");
    context.getWorld().setBlockState(pos.east(), LauncherContent.FRAME.getDefaultState());
    check(
        context,
        !dev.icbm.port.launcher.LauncherNetwork.hasFrame(context.getWorld(), pos),
        "Tier three needs three frame sides");
    context.getWorld().setBlockState(pos.west(), LauncherContent.FRAME.getDefaultState());
    context.getWorld().setBlockState(pos.north(), LauncherContent.FRAME.getDefaultState());
    check(
        context,
        dev.icbm.port.launcher.LauncherNetwork.hasFrame(context.getWorld(), pos),
        "Three frame sides must satisfy tier three");
    LauncherBlockEntity launcher = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos);
    NbtCompound nbt = new NbtCompound();
    nbt.putInt("Energy", 100000);
    launcher.readNbt(nbt);
    check(context, launcher.energy() == 1200, "Tier capacity must clamp saved energy");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void connectorRemovalBreaksNetwork(TestContext context) {
    LauncherBlockEntity launcher = launcher(context);
    BlockPos pos = launcher.getPos();
    context.getWorld().setBlockState(pos.east(), LauncherContent.CONNECTOR.getDefaultState());
    context.getWorld().setBlockState(pos.east(2), LauncherContent.SCREEN_BLOCK.getDefaultState());
    check(
        context,
        dev.icbm.port.launcher.LauncherNetwork.connected(context.getWorld(), pos)
            .contains(pos.east(2)),
        "Connector must route to screen");
    context.getWorld().removeBlock(pos.east(), false);
    check(
        context,
        !dev.icbm.port.launcher.LauncherNetwork.connected(context.getWorld(), pos)
            .contains(pos.east(2)),
        "Removing connector must revoke network path");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void restoredOutOfTierTargetCannotLaunch(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context.getWorld().setBlockState(pos, LauncherContent.BASE_TIER_1.getDefaultState());
    context.getWorld().setBlockState(pos.east(), LauncherContent.FRAME.getDefaultState());
    LauncherBlockEntity launcher = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos);
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    NbtCompound nbt = new NbtCompound();
    launcher.writeNbt(nbt);
    nbt.putLong("Target", pos.east(LauncherContent.BASE_TIER_1.range() + 1).asLong());
    nbt.putBoolean("Targeted", true);
    launcher.readNbt(nbt);
    check(
        context,
        !LauncherContent.BASE_TIER_1.launch(context.getWorld(), pos),
        "Restored target exceeding tier range must not launch");
    check(context, !launcher.getStack(0).isEmpty(), "Invalid saved target must preserve ammo");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void launcherPreservesClusterDelivery(TestContext context) {
    LauncherBlockEntity launcher = launcher(context);
    ItemStack cluster = new ItemStack(dev.icbm.port.delivery.DeliveryContent.CLUSTER_MISSILE);
    var plan =
        new dev.icbm.port.delivery.ClusterPlan(
            java.util.List.of(dev.icbm.port.payload.PayloadType.CONDENSED));
    plan.writeTo(cluster);
    launcher.setStack(0, cluster);
    check(
        context,
        IcbmPort.LAUNCHER.launch(context.getWorld(), launcher.getPos()),
        "Cluster ammo must launch");
    var missiles =
        context
            .getWorld()
            .getEntitiesByClass(
                dev.icbm.port.entity.MissileEntity.class,
                new net.minecraft.util.math.Box(launcher.getPos()).expand(3),
                e -> true);
    check(
        context,
        missiles.size() == 1
            && missiles.get(0).isCluster()
            && missiles.get(0) instanceof dev.icbm.port.entity.ActionableMissileEntity,
        "Launcher must fire cluster ammo as an actionable cluster missile");
    missiles.forEach(dev.icbm.port.entity.MissileEntity::discard);
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void emptyClusterPreservesAmmoFuelAndEnergy(TestContext context) {
    LauncherBlockEntity launcher = launcher(context);
    NbtCompound saved = new NbtCompound();
    launcher.writeNbt(saved);
    saved.putInt("Energy", 0);
    launcher.readNbt(saved);
    launcher.setStack(0, new ItemStack(dev.icbm.port.delivery.DeliveryContent.CLUSTER_MISSILE));
    launcher.setStack(1, new ItemStack(Items.REDSTONE));
    check(
        context,
        !IcbmPort.LAUNCHER.launch(context.getWorld(), launcher.getPos()),
        "Empty cluster casing must not launch");
    check(
        context,
        launcher.energy() == 0
            && launcher.getStack(1).getCount() == 1
            && !launcher.getStack(0).isEmpty(),
        "Invalid cluster must preserve ammunition, stored energy and fuel");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void heldItemLauncherPreservesCargoAndChargesCost(TestContext context) {
    LauncherBlockEntity launcher = launcher(context);
    ItemStack ammo = new ItemStack(dev.icbm.port.delivery.DeliveryContent.HELD_ITEM_MISSILE);
    dev.icbm.port.delivery.HeldItemMissileItem.setCargo(ammo, new ItemStack(Items.DIAMOND_SWORD));
    check(
        context,
        launcher.isValid(0, ammo),
        "Loaded held-item missile must be accepted by launcher");
    launcher.setStack(0, ammo);
    check(
        context,
        IcbmPort.LAUNCHER.launch(context.getWorld(), launcher.getPos()),
        "Loaded held-item missile must launch");
    var missiles =
        context
            .getWorld()
            .getEntitiesByType(
                dev.icbm.port.delivery.DeliveryContent.HELD_MISSILE,
                new net.minecraft.util.math.Box(launcher.getPos()).expand(3),
                e -> true);
    check(
        context,
        missiles.size() == 1
            && dev.icbm.port.delivery.HeldItemMissileItem.cargo(missiles.get(0).getMissile())
                .isOf(Items.DIAMOND_SWORD),
        "Launcher must retain held item cargo");
    check(
        context,
        launcher.getStack(0).isEmpty() && launcher.energy() == 0,
        "Held-item launch must consume one missile and normal charge");
    missiles.forEach(net.minecraft.entity.Entity::discard);
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void emptyHeldItemCasingCannotFire(TestContext context) {
    LauncherBlockEntity launcher = launcher(context);
    ItemStack casing = new ItemStack(dev.icbm.port.delivery.DeliveryContent.HELD_ITEM_MISSILE);
    check(context, !launcher.isValid(0, casing), "Empty held-item casing must not enter launcher");
    launcher.setStack(0, casing);
    check(
        context,
        !IcbmPort.LAUNCHER.launch(context.getWorld(), launcher.getPos()),
        "Restored empty held casing must not fire");
    check(
        context,
        launcher.energy() == 1000 && !launcher.getStack(0).isEmpty(),
        "Rejected held casing must preserve energy and item");
    context.complete();
  }
}
