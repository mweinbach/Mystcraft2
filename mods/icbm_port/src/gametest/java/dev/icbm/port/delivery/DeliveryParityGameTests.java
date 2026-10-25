package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.block.LauncherBlock;
import dev.icbm.port.defense.DefenseContent;
import dev.icbm.port.defense.DefuserItem;
import dev.icbm.port.defense.RadarGunItem;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import dev.icbm.port.launcher.LauncherBlockEntity;
import dev.icbm.port.payload.PayloadType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

/**
 * Behaviour contracts for delivery items/entities against MIT ICBM-Classic (EntityGrenade,
 * EntityExplosive, BlockExplosive, ItemDefuser, EntityBombDroplet, ActionCluster, RecipeCluster,
 * EntityBalloon, ItemRocketLauncher, ItemRadarGun, ItemAntidote, loot hooks). Only inert SMOKE
 * payloads are detonated here; the EMP sweep runs in its own batch because it is area-wide.
 */
public final class DeliveryParityGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean pass, String message) {
    if (!pass) c.throwGameTestException(message);
  }

  private static <T extends Entity> java.util.List<T> near(
      TestContext c, Class<T> type, BlockPos pos, double range) {
    return c.getWorld().getEntitiesByClass(type, new Box(pos).expand(range), e -> true);
  }

  private static java.util.List<ItemEntity> drops(
      TestContext c, BlockPos pos, net.minecraft.item.Item item) {
    return c.getWorld()
        .getEntitiesByClass(
            ItemEntity.class, new Box(pos).expand(3), e -> e.getStack().isOf(item));
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void grenadeFuseUsesConfigAndFiresAfterFullDuration(TestContext c) {
    GrenadeEntity grenade = new GrenadeEntity(DeliveryContent.GRENADE, c.getWorld());
    grenade.setPayload(PayloadType.SMOKE);
    check(
        c,
        grenade.getFuse() == PayloadType.SMOKE.grenadeFuseTicks()
            && PayloadType.CONDENSED.grenadeFuseTicks() == 100
            && PayloadType.ATTRACTIVE.grenadeFuseTicks() == 120,
        "Grenade fuse must come from ConfigFuseTimes.GRENADES defaults");
    grenade.setFuse(3);
    grenade.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 6, 2))));
    grenade.setNoGravity(true);
    c.getWorld().spawnEntity(grenade);
    for (int i = 0; i < 3; i++) grenade.tick();
    check(c, !grenade.isRemoved(), "A 3-tick fuse must survive three ticks (ticksExisted > fuse)");
    grenade.tick();
    check(c, grenade.isRemoved(), "A 3-tick fuse must detonate on the fourth tick");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void grenadeThrowFollowsUpstreamVectorAndConsumption(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    var item = (GrenadeItem) DeliveryContent.GRENADES.get(PayloadType.SMOKE);
    for (boolean creative : new boolean[] {false, true}) {
      var player = c.createMockPlayer();
      player.getAbilities().creativeMode = creative;
      player.setPosition(Vec3d.ofBottomCenter(pos));
      player.setYaw(0);
      player.setHeadYaw(0);
      player.setPitch(0);
      ItemStack stack = new ItemStack(item, 4);
      check(c, GrenadeItem.charge(0) == 1F && GrenadeItem.charge(30) == .5F, "Charge curve");
      item.onStoppedUsing(stack, c.getWorld(), player, 0);
      var thrown = near(c, GrenadeEntity.class, pos, 4);
      check(c, thrown.size() == 1, "Exactly one grenade must be thrown, saw " + thrown.size());
      GrenadeEntity grenade = thrown.get(0);
      double speed = grenade.getVelocity().length();
      check(
          c,
          Math.abs(speed - GrenadeEntity.THROW_SPEED) < .1,
          "Full-charge throw must leave at upstream speed 1.8, got " + speed);
      check(
          c,
          Math.abs(grenade.getY() - (player.getEyeY() - .1)) < .01,
          "Grenade must spawn 0.1 below the thrower's eyes");
      check(c, grenade.getOwner() == player, "Thrower must own the grenade");
      check(
          c,
          stack.getCount() == (creative ? 4 : 3),
          "Survival throws consume one grenade, creative throws none");
      grenade.discard();
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void primedExplosivePopsAndDetonatesAfterFuse(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    var block = IcbmPort.EXPLOSIVE_BLOCKS.get(PayloadType.SMOKE);
    c.getWorld().setBlockState(pos, block.getDefaultState());
    check(c, block.prime(c.getWorld(), pos, 2), "Explosive must prime");
    c.getWorld().removeBlock(pos, false);
    var primed = near(c, PrimedExplosiveEntity.class, pos, 2);
    check(c, primed.size() == 1, "Exactly one primed explosive must spawn");
    PrimedExplosiveEntity entity = primed.get(0);
    check(
        c,
        Math.abs(entity.getVelocity().y - .2) < 1e-6
            && entity.getVelocity().horizontalLength() <= .0201,
        "Priming must apply upstream's 0.2 upward pop and 0.02 drift");
    check(
        c,
        Math.abs(entity.detonationPoint().y - entity.getY() - .5) < 1e-9,
        "Detonation point must be the middle of the body");
    entity.tick();
    entity.tick();
    check(c, !entity.isRemoved(), "A 2-tick fuse must survive two ticks (fuse-- < 1)");
    entity.tick();
    check(c, entity.isRemoved(), "A 2-tick fuse must detonate on the third tick");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void chainReactionUsesPayloadBlockFuse(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    var block = IcbmPort.EXPLOSIVE_BLOCKS.get(PayloadType.SMOKE);
    c.getWorld().setBlockState(pos, block.getDefaultState());
    c.getWorld().removeBlock(pos, false);
    var explosion =
        new net.minecraft.world.explosion.Explosion(
            c.getWorld(), null, pos.getX(), pos.getY(), pos.getZ(), 1F);
    block.onDestroyedByExplosion(c.getWorld(), pos, explosion);
    var primed = near(c, PrimedExplosiveEntity.class, pos, 2);
    check(
        c,
        primed.size() == 1 && primed.get(0).getFuse() == PayloadType.SMOKE.blockFuseTicks(),
        "Explosion chain priming must use the configured block fuse, as upstream does");
    primed.forEach(Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void explosivePlacedBesideFirePrimesBurning(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    var world = c.getWorld();
    world.setBlockState(pos.down(), Blocks.STONE.getDefaultState());
    world.setBlockState(pos.east().down(), Blocks.STONE.getDefaultState());
    world.setBlockState(pos.east(), Blocks.FIRE.getDefaultState());
    var block = IcbmPort.EXPLOSIVE_BLOCKS.get(PayloadType.SMOKE);
    var state = block.getDefaultState();
    world.setBlockState(pos, state);
    block.onPlaced(world, pos, state, null, new ItemStack(block));
    var primed = near(c, PrimedExplosiveEntity.class, pos, 2);
    check(
        c,
        world.getBlockState(pos).isAir() && primed.size() == 1 && primed.get(0).isOnFire(),
        "Placing an explosive beside fire must prime it already burning");
    primed.forEach(Entity::discard);
    world.removeBlock(pos.east(), false);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbm_pr4_emp_sweep")
  public void empRemovesPrimedExplosivesWithoutDetonating(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    var entity = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, c.getWorld());
    entity.setPayload(PayloadType.SMOKE);
    entity.setFuse(200);
    entity.setPosition(Vec3d.ofBottomCenter(pos));
    c.getWorld().spawnEntity(entity);
    // Only the primed-explosive handler: the full EMP also leaves launcher-disable zones that
    // would outlive this test and break later launcher tests at the same structure position.
    PrimedExplosiveEntity.empKill(c.getWorld(), Vec3d.ofCenter(pos));
    check(c, entity.isRemoved(), "EMP must remove primed explosives (upstream CapabilityEmpKill)");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void defuserDisarmsThrownAndPrimedExplosives(TestContext c) {
    var world = c.getWorld();
    var pos = c.getAbsolutePos(new BlockPos(2, 3, 2));
    GrenadeEntity grenade = new GrenadeEntity(DeliveryContent.GRENADE, world);
    grenade.setPayload(PayloadType.SMOKE);
    grenade.setItem(new ItemStack(DeliveryContent.GRENADES.get(PayloadType.SMOKE)));
    grenade.setPosition(Vec3d.ofCenter(pos));
    world.spawnEntity(grenade);
    BombletEntity bomblet = new BombletEntity(DeliveryContent.BOMBLET, world);
    bomblet.setPayload(PayloadType.SMOKE);
    bomblet.setPosition(Vec3d.ofCenter(pos));
    world.spawnEntity(bomblet);
    PrimedExplosiveEntity primed = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, world);
    primed.setPayload(PayloadType.SMOKE);
    primed.setPosition(Vec3d.ofCenter(pos));
    world.spawnEntity(primed);
    BombCartEntity cart = new BombCartEntity(DeliveryContent.BOMB_CART, world);
    cart.setPayload(PayloadType.SMOKE);
    cart.setPosition(Vec3d.ofCenter(pos));
    cart.prime();
    world.spawnEntity(cart);
    for (Entity e : new Entity[] {grenade, bomblet, primed, cart})
      check(c, DefuserItem.defuse(e) && e.isRemoved(), "Defuser must disarm " + e.getType());
    check(
        c,
        drops(c, pos, DeliveryContent.GRENADES.get(PayloadType.SMOKE)).size() == 1
            && drops(c, pos, DeliveryContent.BOMBLETS.get(PayloadType.SMOKE)).size() == 1
            && drops(c, pos, IcbmPort.EXPLOSIVE_BLOCKS.get(PayloadType.SMOKE).asItem()).size()
                == 1
            && drops(c, pos, DeliveryContent.BOMB_CARTS.get(PayloadType.SMOKE)).size() == 1,
        "Each disarmed explosive must drop exactly its own item");
    check(
        c,
        !DefenseContent.DEFUSER.isDamageable() && DefenseContent.DEFUSER.getMaxCount() == 1,
        "Upstream defuser never wears out");
    world
        .getEntitiesByClass(ItemEntity.class, new Box(pos).expand(3), e -> true)
        .forEach(Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void bombletIgnoresDamageAndUsesUpstreamBallistics(TestContext c) {
    BombletEntity bomblet = new BombletEntity(DeliveryContent.BOMBLET, c.getWorld());
    bomblet.setPayload(PayloadType.SMOKE);
    bomblet.setPosition(Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 8, 2))));
    c.getWorld().spawnEntity(bomblet);
    check(
        c,
        !bomblet.damage(DamageSource.IN_FIRE, 5)
            && !bomblet.damage(DamageSource.explosion((net.minecraft.entity.LivingEntity) null), 5)
            && !bomblet.isRemoved(),
        "Bomblets have no health upstream: fire and blasts must not set them off");
    bomblet.setVelocity(Vec3d.ZERO);
    bomblet.tick();
    check(
        c,
        Math.abs(bomblet.getVelocity().y + BombletEntity.GRAVITY) < 1e-6,
        "Droplet gravity must be upstream's 0.05, got " + bomblet.getVelocity().y);
    check(c, BombletEntity.impactDamage(1.0) == 3F, "Impact damage is ceil(speed * 3)");
    bomblet.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void clusterVolumeAndHeadingMatchUpstream(TestContext c) {
    check(
        c,
        DeliveryConfig.clusterMaxVolume() == 200
            && DeliveryConfig.clusterSize(new ItemStack(DeliveryContent.CLUSTER_MISSILE)) == 20
            && DeliveryConfig.clusterSize(
                    new ItemStack(DeliveryContent.BOMBLETS.get(PayloadType.SMOKE)))
                == 2,
        "Cluster volume defaults must match ConfigClusterMissile/ClusterMissileHandler");
    var player = c.createMockPlayer();
    var handler = new net.minecraft.screen.CraftingScreenHandler(0, player.getInventory());
    var inventory = new net.minecraft.inventory.CraftingInventory(handler, 3, 3);
    var recipe = new ClusterLoadingRecipe(IcbmPort.id("pr4_cluster_volume"));
    ItemStack cluster = new ItemStack(DeliveryContent.CLUSTER_MISSILE);
    new ClusterPlan(java.util.Collections.nCopies(89, PayloadType.SMOKE)).writeTo(cluster);
    inventory.setStack(0, cluster);
    inventory.setStack(1, new ItemStack(DeliveryContent.BOMBLETS.get(PayloadType.SMOKE)));
    ItemStack full = recipe.craft(inventory);
    check(c, ClusterPlan.fromStack(full).size() == 90, "Default volume must hold 90 bomblets");
    inventory.setStack(0, full);
    check(c, !recipe.matches(inventory, c.getWorld()), "A 91st bomblet must not fit");
    // A downward carrier ejects a horizontal first disc around the burst point.
    var pos = c.getAbsolutePos(new BlockPos(2, 6, 2));
    var center = Vec3d.ofCenter(pos);
    int spawned =
        DeliveryContent.burst(
            c.getWorld(), center, PayloadType.SMOKE, null, new Vec3d(0, -1, 0), 10);
    var bomblets = near(c, BombletEntity.class, pos, 3);
    check(c, spawned == 10 && bomblets.size() == 10, "Ten droplets per disc");
    for (BombletEntity b : bomblets) {
      check(c, Math.abs(b.getY() - center.y) < 1e-3, "First disc must be level for a dive");
      check(
          c,
          Math.abs(Math.hypot(b.getX() - center.x, b.getZ() - center.z) - .25) < 1e-3,
          "Droplets start 0.25 from the carrier");
    }
    bomblets.forEach(Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void balloonReleasesCargoAtUpstreamAirKill(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 8, 2));
    CargoEntity balloon = new CargoEntity(DeliveryContent.CARGO, c.getWorld());
    balloon.setBalloon(true);
    balloon.setCargo(new ItemStack(net.minecraft.item.Items.EMERALD, 2));
    NbtCompound nbt = new NbtCompound();
    balloon.writeCustomDataToNbt(nbt);
    nbt.putInt("CarrierAge", 1198);
    nbt.putInt("LiftTicks", 600);
    balloon.readCustomDataFromNbt(nbt);
    balloon.setPosition(Vec3d.ofCenter(pos));
    c.getWorld().spawnEntity(balloon);
    balloon.tick();
    check(c, !balloon.isRemoved(), "Balloon must still fly before 1200 ticks");
    balloon.tick();
    check(c, balloon.isRemoved(), "Balloon must expire at upstream's 1200-tick air limit");
    var emeralds = drops(c, pos, net.minecraft.item.Items.EMERALD);
    check(
        c,
        emeralds.size() == 1 && emeralds.get(0).getStack().getCount() == 2,
        "Expired balloon must drop its cargo exactly once");
    emeralds.forEach(Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void handheldItemsMatchUpstreamStacksAndWear(TestContext c) {
    check(
        c,
        DeliveryContent.ROCKET_LAUNCHER.getMaxCount() == 1
            && !DeliveryContent.ROCKET_LAUNCHER.isDamageable()
            && !DeliveryContent.BALLISTIC_LAUNCHER.isDamageable(),
        "Rocket launchers have no wear upstream");
    check(c, DefenseContent.ANTIDOTE.getMaxCount() == 64, "Antidote stacks to 64 upstream");
    check(c, DefenseContent.RADAR_GUN.getMaxCount() == 1, "Radar gun stacks to 1");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void ballisticClusterAimsAboveTarget(TestContext c) {
    var player = c.createMockPlayer();
    player.getAbilities().creativeMode = false;
    var pos = c.getAbsolutePos(new BlockPos(2, 3, 2)).withY(c.getWorld().getTopY() - 60);
    player.setPosition(Vec3d.ofCenter(pos));
    player.setYaw(0);
    player.setHeadYaw(0);
    player.setBodyYaw(0);
    player.setPitch(0);
    var launcher = new ItemStack(DeliveryContent.BALLISTIC_LAUNCHER);
    var ammo = new ItemStack(DeliveryContent.CLUSTER_MISSILE, 1);
    new ClusterPlan(java.util.List.of(PayloadType.SMOKE)).writeTo(ammo);
    player.getInventory().setStack(0, ammo);
    var far = new BlockPos(player.getEyePos().add(0, 0, 30));
    c.getWorld().setBlockState(far, Blocks.STONE.getDefaultState());
    ((RocketLauncherItem) DeliveryContent.BALLISTIC_LAUNCHER)
        .onStoppedUsing(launcher, c.getWorld(), player, 71980);
    var missiles =
        c.getWorld()
            .getEntitiesByClass(
                dev.icbm.port.entity.MissileEntity.class, new Box(pos).expand(4), e -> true);
    check(
        c,
        missiles.size() == 1
            && ammo.isEmpty()
            && missiles.get(0) instanceof dev.icbm.port.entity.ActionableMissileEntity,
        "Cluster missile must launch once as an actionable cluster missile");
    NbtCompound flight = new NbtCompound();
    missiles.get(0).writeCustomDataToNbt(flight);
    check(
        c,
        flight.getCompound("FlightPlan").getLong("Target")
            == far.up(RocketLauncherItem.BALLISTIC_BURST_Y).asLong(),
        "Ballistic cluster aim must be raised by upstream ballisticBurstY (20)");
    missiles.forEach(Entity::discard);
    c.getWorld().removeBlock(far, false);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void radarGunStoresPointAndConfiguresLauncher(TestContext c) {
    var world = c.getWorld();
    var launcherPos = c.getAbsolutePos(new BlockPos(1, 2, 1));
    world.setBlockState(
        launcherPos, IcbmPort.LAUNCHER.getDefaultState().with(LauncherBlock.FACING, Direction.UP));
    var launcher = (LauncherBlockEntity) world.getBlockEntity(launcherPos);
    ItemStack gun = new ItemStack(DefenseContent.RADAR_GUN);
    check(
        c,
        !RadarGunItem.copyToLauncher(world, gun, launcher),
        "An empty radar gun must not configure a launcher");
    Vec3d point = Vec3d.of(c.getAbsolutePos(new BlockPos(3, 1, 3))).add(.25, .5, .75);
    RadarGunItem.record(world, gun, point);
    var stored = gun.getSubNbt(RadarGunItem.NBT_KEY);
    check(
        c,
        stored.getDouble("X") == point.x
            && stored.getDouble("Z") == point.z
            && RadarGunItem.storedPosition(gun).equals(new BlockPos(point))
            && RadarGunItem.storedDimension(gun).equals(world.getRegistryKey().getValue()),
        "Radar gun must keep the precise point and dimension");
    check(
        c,
        RadarGunItem.copyToLauncher(world, gun, launcher)
            && launcher.target().equals(new BlockPos(point))
            && launcher.targeted(),
        "Radar gun must hand its target to the launcher");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void dungeonChestsCarryUpstreamComponents(TestContext c) {
    var world = c.getWorld();
    var limits =
        java.util.Map.of(
            "steel_ingot", 9,
            "steel_plate", 4,
            "copper_wire", 19,
            "gold_wire", 14,
            "basic_circuit", 14,
            "advanced_circuit", 10,
            "elite_circuit", 7);
    var seen = new java.util.HashSet<String>();
    for (var table :
        new net.minecraft.util.Identifier[] {
          dev.icbm.port.content.Materials.MINESHAFT, dev.icbm.port.content.Materials.DUNGEON
        }) {
      var loot = world.getServer().getLootManager().getTable(table);
      for (int seed = 0; seed < 40; seed++) {
        var context =
            new LootContext.Builder(world)
                .parameter(
                    LootContextParameters.ORIGIN,
                    Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 2, 2))))
                .random(seed)
                .build(LootContextTypes.CHEST);
        for (var stack : loot.generateLoot(context)) {
          var id = Registry.ITEM.getId(stack.getItem());
          if (!id.getNamespace().equals(IcbmPort.MOD_ID)) continue;
          Integer max = limits.get(id.getPath());
          check(c, max != null, "Unexpected ICBM chest loot " + id);
          check(
              c,
              stack.getCount() >= 1 && stack.getCount() <= max,
              id + " stack " + stack.getCount() + " exceeds upstream nextInt bound " + max);
          seen.add(id.getPath());
        }
      }
    }
    check(c, seen.size() >= 3, "Chest loot must offer several upstream components, saw " + seen);
    c.complete();
  }
}
