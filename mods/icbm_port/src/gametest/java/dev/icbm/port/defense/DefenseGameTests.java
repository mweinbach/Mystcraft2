package dev.icbm.port.defense;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.MissileEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class DefenseGameTests implements FabricGameTest {
  private static void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void empDisablesOnlyInsideSphere(TestContext context) {
    BlockPos origin = context.getAbsolutePos(new BlockPos(2, 20, 2));
    MissileEntity near = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    near.setPosition(Vec3d.ofCenter(origin));
    context.getWorld().spawnEntity(near);
    DefenseContent.applyEmp(context.getWorld(), Vec3d.ofCenter(origin), 4);
    check(context, near.isEmpDisabled(), "EMP must disable missiles within its sphere");
    check(
        context,
        DefenseContent.isEmpDisabled(context.getWorld(), origin),
        "EMP must disable launcher electronics");
    check(
        context,
        !DefenseContent.isEmpDisabled(context.getWorld(), origin.add(4, 4, 0)),
        "EMP must use spherical range");
    near.discard();
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void radarCountsLoadedMissiles(TestContext context) {
    BlockPos origin = context.getAbsolutePos(new BlockPos(2, 20, 2));
    context.getWorld().setBlockState(origin, DefenseContent.RADAR_STATION.getDefaultState());
    ((DefenseMachineBlockEntity) context.getWorld().getBlockEntity(origin))
        .energyBuffer()
        .setAmount(20_000);
    MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    missile.setPosition(Vec3d.ofCenter(origin.up(3)));
    context.getWorld().spawnEntity(missile);
    context.runAtTick(
        12,
        () -> {
          check(
              context,
              context.getWorld().getBlockState(origin).get(DefenseMachineBlock.POWER) >= 1,
              "Radar must expose missile tracks as redstone output");
          missile.discard();
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void poweredSamConsumesOneInterceptor(TestContext context) {
    BlockPos origin = context.getAbsolutePos(new BlockPos(2, 20, 2));
    context
        .getWorld()
        .setBlockState(
            origin,
            DefenseContent.SAM_LAUNCHER.getDefaultState().with(DefenseMachineBlock.POWER, 2));
    context
        .getWorld()
        .setBlockState(origin.down(), net.minecraft.block.Blocks.REDSTONE_BLOCK.getDefaultState());
    MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    missile.setPosition(Vec3d.ofCenter(origin.up(3)));
    context.getWorld().spawnEntity(missile);
    context.runAtTick(
        12,
        () -> {
          check(context, missile.isRemoved(), "Powered SAM must intercept a loaded missile");
          check(
              context,
              context.getWorld().getBlockState(origin).get(DefenseMachineBlock.POWER) == 1,
              "An interception must consume exactly one charge");
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void interceptorPersistsTargetAndLifetime(TestContext context) {
    MissileEntity target = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    SamInterceptorEntity interceptor =
        new SamInterceptorEntity(DefenseContent.SAM_INTERCEPTOR, context.getWorld());
    interceptor.setTarget(target);
    net.minecraft.nbt.NbtCompound saved = interceptor.writeNbt(new net.minecraft.nbt.NbtCompound());
    saved.putInt("FlightTicks", 77);
    SamInterceptorEntity restored =
        new SamInterceptorEntity(DefenseContent.SAM_INTERCEPTOR, context.getWorld());
    restored.readNbt(saved);
    check(
        context,
        target.getUuid().equals(restored.getTargetUuid()),
        "SAM must retain its exact target UUID");
    check(context, restored.getFlightTicks() == 77, "Reload must preserve elapsed flight lifetime");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void samIgnoresRecedingMissiles(TestContext context) {
    Vec3d station = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 20, 2)));
    MissileEntity target = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    target.setPosition(station.add(10, 0, 0));
    target.setVelocity(1, 0, 0);
    check(
        context,
        !DefenseContent.isThreat(context.getWorld(), station, target),
        "Receding missiles must not consume interceptors");
    target.setVelocity(-1, 0, 0);
    check(
        context,
        DefenseContent.isThreat(context.getWorld(), station, target),
        "Approaching missiles must be classified as threats");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void interceptorCannotFlyThroughWall(TestContext context) {
    BlockPos start = context.getAbsolutePos(new BlockPos(2, 20, 2));
    MissileEntity target = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    target.setPosition(Vec3d.ofCenter(start.south(6)));
    context.getWorld().spawnEntity(target);
    context
        .getWorld()
        .setBlockState(start.south(2), net.minecraft.block.Blocks.BEDROCK.getDefaultState());
    SamInterceptorEntity interceptor =
        new SamInterceptorEntity(DefenseContent.SAM_INTERCEPTOR, context.getWorld());
    interceptor.setPosition(Vec3d.ofCenter(start));
    interceptor.setTarget(target);
    interceptor.setVelocity(0, 0, 3);
    context.getWorld().spawnEntity(interceptor);
    context.runAtTick(
        3,
        () -> {
          check(context, interceptor.isRemoved(), "Wall collision must remove interceptor");
          check(
              context,
              !target.isRemoved(),
              "Interceptor must not neutralize a missile through a wall");
          target.discard();
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void empFieldsSurviveReloadAndExpireOnTime(TestContext context) {
    EmpPersistentState original = new EmpPersistentState();
    Vec3d center = new Vec3d(10, 20, 30);
    original.add(center, 4, 1000);
    EmpPersistentState restored =
        EmpPersistentState.fromNbt(original.writeNbt(new net.minecraft.nbt.NbtCompound()));
    check(context, restored.contains(center, 1199), "Reload must retain EMP's remaining lifetime");
    check(
        context,
        !restored.contains(center.add(4, 4, 0), 1199),
        "Reload must preserve spherical EMP bounds");
    check(context, !restored.contains(center, 1200), "Reload must not reset the EMP expiry");
    for (int index = 0; index < 300; index++) original.add(center, 1000, 1000);
    check(context, original.size() == 256, "EMP persistence must bound retained fields");
    EmpPersistentState bounded =
        EmpPersistentState.fromNbt(original.writeNbt(new net.minecraft.nbt.NbtCompound()));
    check(
        context,
        !bounded.contains(center.add(513, 0, 0), 1001),
        "EMP must clamp persisted radius to 512");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void spikeVariantsApplyUpstreamEffects(TestContext context) {
    var victim = net.minecraft.entity.EntityType.PIG.create(context.getWorld());
    check(context, victim != null, "Test victim must exist");
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    DefenseContent.POISON_SPIKES.onEntityCollision(
        DefenseContent.POISON_SPIKES.getDefaultState(), context.getWorld(), pos, victim);
    var poison = victim.getStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON);
    check(
        context,
        poison != null && poison.getDuration() == 140,
        "Poison spikes must apply seven seconds of poison");
    DefenseContent.FIRE_SPIKES.onEntityCollision(
        DefenseContent.FIRE_SPIKES.getDefaultState(), context.getWorld(), pos, victim);
    check(context, victim.isOnFire(), "Fire spikes must ignite colliding living entities");
    check(
        context,
        DefenseContent.SPIKES
            .getDefaultState()
            .getCollisionShape(context.getWorld(), pos)
            .isEmpty(),
        "Spikes must allow overlap collisions");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void heldMissilesAreTrackedAndCountered(TestContext context) {
    Vec3d center = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 20, 2)));
    var held =
        new dev.icbm.port.delivery.HeldItemMissileEntity(
            dev.icbm.port.delivery.DeliveryContent.HELD_MISSILE, context.getWorld());
    held.setMissile(
        new net.minecraft.item.ItemStack(dev.icbm.port.delivery.DeliveryContent.HELD_ITEM_MISSILE));
    held.setPosition(center.add(4, 0, 0));
    held.setVelocity(-1, 0, 0);
    context.getWorld().spawnEntity(held);
    check(
        context,
        DefenseContent.trackedMissiles(context.getWorld(), center, 8).contains(held),
        "Radar must track held-item missile flights");
    check(
        context,
        DefenseContent.isThreat(context.getWorld(), center, held),
        "SAM must classify incoming held missiles");
    check(context, DefenseContent.canDefuse(held), "Defuser must accept held missiles");
    DefenseContent.applyEmp(context.getWorld(), center, 8);
    check(
        context,
        held.isRemoved() && held.isEmpDisabled(),
        "EMP must neutralize held flight without executing cargo");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void samInterceptsHeldMissileWithoutCargoImpact(TestContext context) {
    Vec3d center = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 20, 2)));
    var held =
        new dev.icbm.port.delivery.HeldItemMissileEntity(
            dev.icbm.port.delivery.DeliveryContent.HELD_MISSILE, context.getWorld());
    held.setMissile(
        new net.minecraft.item.ItemStack(dev.icbm.port.delivery.DeliveryContent.HELD_ITEM_MISSILE));
    held.setPosition(center.add(0, 0, 4));
    context.getWorld().spawnEntity(held);
    var interceptor = new SamInterceptorEntity(DefenseContent.SAM_INTERCEPTOR, context.getWorld());
    interceptor.setPosition(center);
    interceptor.setTarget(held);
    interceptor.setVelocity(0, 0, 3);
    context.getWorld().spawnEntity(interceptor);
    context.runAtTick(
        3,
        () -> {
          check(context, held.isRemoved(), "SAM must neutralize held-item missile family");
          check(
              context,
              interceptor.isRemoved(),
              "SAM must consume interceptor when held target is caught");
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void defenseConfigurationAndEnergyPersist(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 20, 2));
    context.getWorld().setBlockState(pos, DefenseContent.RADAR_STATION.getDefaultState());
    var machine = (DefenseMachineBlockEntity) context.getWorld().getBlockEntity(pos);
    check(
        context, machine.energyBuffer().getAmount() == 0, "New radar must not receive free energy");
    machine.energyBuffer().setAmount(12_000);
    machine.configure(1);
    machine.configure(5);
    machine.configure(7);
    var saved = machine.createNbt();
    var restored =
        new DefenseMachineBlockEntity(pos, DefenseContent.RADAR_STATION.getDefaultState());
    restored.readNbt(saved);
    check(context, restored.properties().get(0) == 110, "Detection range must persist");
    check(
        context,
        restored.properties().get(2) == 1 && restored.properties().get(4) == 0,
        "Channel and filters must persist");
    check(
        context,
        restored.energyBuffer().getAmount() == 12_000,
        "Stored energy must persist exactly");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void empExtensionsAndFireCost(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 200, 2));
    context.getWorld().setBlockState(pos, DefenseContent.EMP_TOWER.getDefaultState());
    var machine = (DefenseMachineBlockEntity) context.getWorld().getBlockEntity(pos);
    context
        .getWorld()
        .setBlockState(pos.up(), DefenseContent.EMP_TOWER_EXTENSION.getDefaultState());
    context
        .getWorld()
        .setBlockState(pos.up(2), DefenseContent.EMP_TOWER_EXTENSION.getDefaultState());
    check(
        context,
        machine.extensions() == 2 && machine.maximumRange() == 190,
        "Connected boosters must raise max radius by twenty each");
    check(context, !machine.fireEmp(), "Uncharged EMP tower must not fire");
    machine.energyBuffer().setAmount(400_000);
    check(context, machine.fireEmp(), "Charged tower must fire");
    check(
        context,
        machine.energyBuffer().getAmount() == 40_000,
        "Default range60 must consume 60 squared times100 energy");
    check(context, !machine.fireEmp(), "Cooldown must prevent repeat firing");
    context.getWorld().breakBlock(pos, false);
    check(
        context,
        context.getWorld().isAir(pos.up()) && context.getWorld().isAir(pos.up(2)),
        "Removing base must tear down orphan extenders");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void radioChannelsDeliverOnceAndRespectIsolation(TestContext context) {
    var world = context.getWorld();
    BlockPos origin = context.getAbsolutePos(new BlockPos(2, 40, 2));
    class Receiver implements dev.icbm.port.radio.RadioNetwork.Receiver {
      final BlockPos pos;
      final int channel;
      int received;

      Receiver(BlockPos pos, int channel) {
        this.pos = pos;
        this.channel = channel;
      }

      public BlockPos radioPosition() {
        return pos;
      }

      public int radioChannel() {
        return channel;
      }

      public boolean radioEnabled() {
        return true;
      }

      public boolean receive(dev.icbm.port.radio.RadioNetwork.Incoming message) {
        received++;
        return true;
      }
    }
    var sender = new Receiver(origin, 4093);
    var receiver = new Receiver(origin.up(), 4093);
    var isolated = new Receiver(origin.up(2), 4092);
    dev.icbm.port.radio.RadioNetwork.register(world, sender);
    dev.icbm.port.radio.RadioNetwork.register(world, receiver);
    dev.icbm.port.radio.RadioNetwork.register(world, isolated);
    int delivered =
        dev.icbm.port.radio.RadioNetwork.broadcast(world, sender, java.util.UUID.randomUUID());
    check(
        context,
        delivered == 1 && receiver.received == 1,
        "Radio relay loops must not redeliver a message");
    check(context, isolated.received == 0, "Different channels must remain isolated");
    dev.icbm.port.radio.RadioNetwork.remove(world, origin);
    dev.icbm.port.radio.RadioNetwork.remove(world, origin.up());
    dev.icbm.port.radio.RadioNetwork.remove(world, origin.up(2));
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void poweredRadarRadioTriggersSam(TestContext context) {
    BlockPos radarPos = context.getAbsolutePos(new BlockPos(2, 100, 2)), samPos = radarPos.east(4);
    context.getWorld().setBlockState(radarPos, DefenseContent.RADAR_STATION.getDefaultState());
    context
        .getWorld()
        .setBlockState(
            samPos,
            DefenseContent.SAM_LAUNCHER.getDefaultState().with(DefenseMachineBlock.POWER, 1));
    var radar = (DefenseMachineBlockEntity) context.getWorld().getBlockEntity(radarPos);
    var sam = (DefenseMachineBlockEntity) context.getWorld().getBlockEntity(samPos);
    radar.configure(5);
    sam.configure(5);
    radar.energyBuffer().setAmount(20_000);
    var target = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    target.setPosition(Vec3d.ofCenter(samPos.up(3)));
    context.getWorld().spawnEntity(target);
    DefenseMachineBlockEntity.tick(
        context.getWorld(), samPos, context.getWorld().getBlockState(samPos), sam);
    DefenseMachineBlockEntity.tick(
        context.getWorld(), radarPos, context.getWorld().getBlockState(radarPos), radar);
    check(
        context,
        context.getWorld().getBlockState(samPos).get(DefenseMachineBlock.POWER) == 0,
        "Powered radar radio must launch SAM without local redstone");
    check(
        context,
        radar.energyBuffer().getAmount() == 19_000,
        "Radar must consume the upstream per-tick scan energy");
    context.runAtTick(
        4,
        () -> {
          check(
              context,
              target.isRemoved(),
              "Radio-directed interceptor must catch incoming missile");
          context.complete();
        });
  }
}
