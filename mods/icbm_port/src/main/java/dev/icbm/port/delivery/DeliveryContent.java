package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.payload.PayloadType;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.*;
import net.minecraft.item.Item;
import net.minecraft.util.registry.Registry;

/** Delivery forms follow ExplosiveInit's tier-based content registration. */
public final class DeliveryContent {
  public static final net.minecraft.recipe.SpecialRecipeSerializer<CarrierLoadingRecipe>
      CARRIER_LOADING =
          new net.minecraft.recipe.SpecialRecipeSerializer<>(CarrierLoadingRecipe::new);
  public static final net.minecraft.recipe.SpecialRecipeSerializer<CarrierUnloadingRecipe>
      CARRIER_UNLOADING =
          new net.minecraft.recipe.SpecialRecipeSerializer<>(CarrierUnloadingRecipe::new);
  public static final Item HELD_ITEM_MISSILE = new HeldItemMissileItem();
  public static final Item BALLISTIC_LAUNCHER =
      new RocketLauncherItem(new Item.Settings().group(IcbmPort.GROUP).maxCount(1), true);
  public static final EntityType<HeldItemMissileEntity> HELD_MISSILE =
      FabricEntityTypeBuilder.<HeldItemMissileEntity>create(
              SpawnGroup.MISC, HeldItemMissileEntity::new)
          .dimensions(EntityDimensions.fixed(.35F, .35F))
          .trackRangeBlocks(128)
          .trackedUpdateRate(1)
          .build();
  public static final net.minecraft.recipe.SpecialRecipeSerializer<HeldItemLoadingRecipe>
      HELD_ITEM_LOADING =
          new net.minecraft.recipe.SpecialRecipeSerializer<>(HeldItemLoadingRecipe::new);
  public static final net.minecraft.recipe.SpecialRecipeSerializer<HeldItemUnloadingRecipe>
      HELD_ITEM_UNLOADING =
          new net.minecraft.recipe.SpecialRecipeSerializer<>(HeldItemUnloadingRecipe::new);
  public static final net.minecraft.recipe.SpecialRecipeSerializer<ClusterLoadingRecipe>
      CLUSTER_LOADING =
          new net.minecraft.recipe.SpecialRecipeSerializer<>(ClusterLoadingRecipe::new);
  public static final Item CARGO_PARACHUTE = new CargoCarrierItem(false);
  public static final Item CARGO_BALLOON = new CargoCarrierItem(true);
  public static final EntityType<CargoEntity> CARGO =
      FabricEntityTypeBuilder.<CargoEntity>create(SpawnGroup.MISC, CargoEntity::new)
          .dimensions(EntityDimensions.fixed(.5F, .5F))
          .trackRangeBlocks(64)
          .trackedUpdateRate(2)
          .build();
  public static final Map<PayloadType, Item> BOMBLETS = new EnumMap<>(PayloadType.class);
  public static final Item CLUSTER_MISSILE = new ClusterMissileItem();
  public static final Map<PayloadType, Item> GRENADES = new EnumMap<>(PayloadType.class);
  public static final Map<PayloadType, Item> BOMB_CARTS = new EnumMap<>(PayloadType.class);
  public static final Item ROCKET_LAUNCHER =
      new RocketLauncherItem(new Item.Settings().group(IcbmPort.GROUP).maxCount(1));
  public static final EntityType<BombletEntity> BOMBLET =
      FabricEntityTypeBuilder.<BombletEntity>create(SpawnGroup.MISC, BombletEntity::new)
          .dimensions(EntityDimensions.fixed(.25F, .25F))
          .trackRangeBlocks(64)
          .trackedUpdateRate(2)
          .build();
  public static final EntityType<GrenadeEntity> GRENADE =
      FabricEntityTypeBuilder.<GrenadeEntity>create(SpawnGroup.MISC, GrenadeEntity::new)
          .dimensions(EntityDimensions.fixed(.25F, .25F))
          .trackRangeBlocks(64)
          .trackedUpdateRate(1)
          .build();
  public static final EntityType<BombCartEntity> BOMB_CART =
      FabricEntityTypeBuilder.<BombCartEntity>create(SpawnGroup.MISC, BombCartEntity::new)
          .dimensions(EntityDimensions.fixed(.98F, .7F))
          .trackRangeBlocks(80)
          .trackedUpdateRate(3)
          .build();

  public static void register() {
    DeliveryConfig.load();
    net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register(
        (player, world, hand, target, hit) -> {
          var item = player.getStackInHand(hand).getItem();
          return item instanceof CargoCarrierItem carrier
              ? carrier.attachToEntity(player, hand, target)
              : net.minecraft.util.ActionResult.PASS;
        });
    Registry.register(Registry.RECIPE_SERIALIZER, IcbmPort.id("carrier_loading"), CARRIER_LOADING);
    Registry.register(
        Registry.RECIPE_SERIALIZER, IcbmPort.id("carrier_unloading"), CARRIER_UNLOADING);
    Registry.register(Registry.ITEM, IcbmPort.id("held_item_missile"), HELD_ITEM_MISSILE);
    Registry.register(Registry.ITEM, IcbmPort.id("ballistic_launcher"), BALLISTIC_LAUNCHER);
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("held_item_missile"), HELD_MISSILE);
    Registry.register(
        Registry.RECIPE_SERIALIZER, IcbmPort.id("held_item_loading"), HELD_ITEM_LOADING);
    Registry.register(
        Registry.RECIPE_SERIALIZER, IcbmPort.id("held_item_unloading"), HELD_ITEM_UNLOADING);
    Registry.register(Registry.RECIPE_SERIALIZER, IcbmPort.id("cluster_loading"), CLUSTER_LOADING);
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("grenade"), GRENADE);
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("bomblet"), BOMBLET);
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("cargo"), CARGO);
    Registry.register(Registry.ITEM, IcbmPort.id("cargo_parachute"), CARGO_PARACHUTE);
    Registry.register(Registry.ITEM, IcbmPort.id("cargo_balloon"), CARGO_BALLOON);
    Registry.register(Registry.ITEM, IcbmPort.id("cluster_missile"), CLUSTER_MISSILE);
    Registry.register(Registry.ENTITY_TYPE, IcbmPort.id("bomb_cart"), BOMB_CART);
    Registry.register(Registry.ITEM, IcbmPort.id("rocket_launcher"), ROCKET_LAUNCHER);
    for (PayloadType payload : PayloadType.values())
      if (payload.hasContent()) {
        if (payload.tier() == 1)
          BOMBLETS.put(
              payload,
              Registry.register(
                  Registry.ITEM,
                  IcbmPort.id(payload.id() + "_bomblet"),
                  new Item(new Item.Settings().group(IcbmPort.GROUP).maxCount(16))));
        BOMB_CARTS.put(
            payload,
            Registry.register(
                Registry.ITEM,
                IcbmPort.id(payload.id() + "_bomb_cart"),
                new BombCartItem(payload)));
        if (payload.tier() == 1)
          GRENADES.put(
              payload,
              Registry.register(
                  Registry.ITEM, IcbmPort.id(payload.id() + "_grenade"), new GrenadeItem(payload)));
      }
    DeliveryDispensers.register();
    dev.icbm.port.entity.PrimedExplosiveEntity.registerEmpKill();
  }

  public static boolean isLoadedHeldMissile(net.minecraft.item.ItemStack stack) {
    return stack.isOf(HELD_ITEM_MISSILE)
        && HeldItemMissileItem.allowed(HeldItemMissileItem.cargo(stack));
  }

  public static boolean launchHeld(
      net.minecraft.world.World world,
      net.minecraft.entity.Entity owner,
      net.minecraft.item.ItemStack ammo,
      net.minecraft.util.math.Vec3d muzzle,
      net.minecraft.util.math.Vec3d velocity,
      net.minecraft.util.math.BlockPos target) {
    return launchHeldEntity(world, owner, ammo, muzzle, velocity, target) != null;
  }

  /** As {@link #launchHeld}, returning the spawned missile (or null). */
  public static HeldItemMissileEntity launchHeldEntity(
      net.minecraft.world.World world,
      net.minecraft.entity.Entity owner,
      net.minecraft.item.ItemStack ammo,
      net.minecraft.util.math.Vec3d muzzle,
      net.minecraft.util.math.Vec3d velocity,
      net.minecraft.util.math.BlockPos target) {
    return launchHeldEntity(world, owner, ammo, muzzle, velocity, target, 3);
  }

  /** As above, climbing to {@code lockHeight} before turning (a launcher's lock-height setting). */
  public static HeldItemMissileEntity launchHeldEntity(
      net.minecraft.world.World world,
      net.minecraft.entity.Entity owner,
      net.minecraft.item.ItemStack ammo,
      net.minecraft.util.math.Vec3d muzzle,
      net.minecraft.util.math.Vec3d velocity,
      net.minecraft.util.math.BlockPos target,
      int lockHeight) {
    if (world.isClient || !isLoadedHeldMissile(ammo)) return null;
    HeldItemMissileEntity missile = new HeldItemMissileEntity(HELD_MISSILE, world);
    missile.setOwner(owner);
    missile.setMissile(ammo);
    missile.setPosition(muzzle);
    missile.setVelocity(velocity);
    if (target != null) {
      missile.flightPlan().setLockHeight(lockHeight);
      try {
        missile.configureTarget(target);
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    return world.spawnEntity(missile) ? missile : null;
  }

  public static PayloadType readPayload(String id) {
    for (PayloadType type : PayloadType.values())
      if (type.id().equals(id) && type.hasContent()) return type;
    return PayloadType.CONDENSED;
  }

  public static int grenadeFuse(PayloadType payload) {
    return payload.grenadeFuseTicks();
  }

  public static PayloadType clusterPayload(net.minecraft.item.ItemStack stack) {
    if (!stack.isOf(CLUSTER_MISSILE)) return null;
    ClusterPlan plan = ClusterPlan.fromStack(stack);
    return plan.isEmpty() ? PayloadType.CONDENSED : plan.payloads().get(0);
  }

  public static PayloadType payloadForBomblet(net.minecraft.item.ItemStack stack) {
    for (var entry : BOMBLETS.entrySet()) if (stack.isOf(entry.getValue())) return entry.getKey();
    return null;
  }

  /** Bounded disc ejection from upstream ActionCluster, ten droplets per layer. */
  public static int burst(
      net.minecraft.world.World world,
      net.minecraft.util.math.Vec3d position,
      PayloadType payload,
      net.minecraft.entity.Entity source,
      net.minecraft.util.math.Vec3d heading) {
    return burst(world, position, payload, source, heading, 16);
  }

  public static int burst(
      net.minecraft.world.World world,
      net.minecraft.util.math.Vec3d position,
      PayloadType payload,
      net.minecraft.entity.Entity source,
      net.minecraft.util.math.Vec3d heading,
      int count) {
    return burst(
        world,
        position,
        new ClusterPlan(
            java.util.Collections.nCopies(
                Math.min(ClusterPlan.MAX_BOMBLETS, Math.max(0, count)), payload)),
        source,
        heading);
  }

  /**
   * Spawns one droplet per manifest entry using {@link ClusterBurst}'s upstream geometry, oriented
   * by the carrier heading (upstream reads the host missile's yaw/pitch). If a droplet cannot be
   * spawned its bomblet item is dropped instead, as upstream ActionCluster does, so nothing is lost.
   */
  public static int burst(
      net.minecraft.world.World world,
      net.minecraft.util.math.Vec3d position,
      ClusterPlan plan,
      net.minecraft.entity.Entity source,
      net.minecraft.util.math.Vec3d heading) {
    if (world.isClient) return 0;
    var random = world.random;
    var ejections =
        ClusterBurst.plan(
            plan.size(),
            ClusterBurst.headingYaw(heading.x, heading.y, heading.z),
            ClusterBurst.headingPitch(heading.x, heading.y, heading.z),
            random::nextFloat);
    int spawned = 0;
    for (ClusterBurst.Ejection ejection : ejections) {
      PayloadType payload = plan.payloads().get(ejection.entry());
      var at = position.add(ejection.offsetX(), ejection.offsetY(), ejection.offsetZ());
      BombletEntity bomblet = new BombletEntity(BOMBLET, world);
      bomblet.setPayload(payload);
      bomblet.setOwner(source);
      bomblet.setPosition(at);
      bomblet.setVelocity(ejection.motionX(), ejection.motionY(), ejection.motionZ());
      if (world.spawnEntity(bomblet)) spawned++;
      else {
        var drop =
            new net.minecraft.entity.ItemEntity(
                world,
                at.x,
                at.y,
                at.z,
                new net.minecraft.item.ItemStack(BOMBLETS.get(payload)),
                ejection.motionX(),
                ejection.motionY(),
                ejection.motionZ());
        drop.setToDefaultPickupDelay();
        world.spawnEntity(drop);
      }
    }
    return spawned;
  }

  private DeliveryContent() {}
}
