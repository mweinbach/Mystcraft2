package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.payload.PayloadType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;

public final class DeliveryGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean pass, String message) {
    if (!pass) c.throwGameTestException(message);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void grenadePayloadAndFuseSurviveReload(TestContext c) {
    GrenadeEntity grenade = new GrenadeEntity(DeliveryContent.GRENADE, c.getWorld());
    grenade.setPayload(PayloadType.REPULSIVE);
    grenade.setFuse(37);
    grenade.setItem(new ItemStack(DeliveryContent.GRENADES.get(PayloadType.REPULSIVE)));
    NbtCompound data = new NbtCompound();
    grenade.writeCustomDataToNbt(data);
    GrenadeEntity restored = new GrenadeEntity(DeliveryContent.GRENADE, c.getWorld());
    restored.readCustomDataFromNbt(data);
    check(
        c,
        restored.getPayload() == PayloadType.REPULSIVE && restored.getFuse() == 37,
        "Grenade must preserve payload and remaining fuse");
    check(
        c,
        restored.getStack().isOf(DeliveryContent.GRENADES.get(PayloadType.REPULSIVE)),
        "Grenade render stack must persist");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void grenadeFuseExpiresOnServer(TestContext c) {
    GrenadeEntity grenade = new GrenadeEntity(DeliveryContent.GRENADE, c.getWorld());
    grenade.setPayload(PayloadType.SMOKE);
    grenade.setFuse(1);
    grenade.setPosition(
        c.getAbsolutePos(new BlockPos(2, 3, 2)).getX(),
        c.getAbsolutePos(new BlockPos(2, 3, 2)).getY(),
        c.getAbsolutePos(new BlockPos(2, 3, 2)).getZ());
    c.getWorld().spawnEntity(grenade);
    grenade.tick();
    check(c, grenade.isRemoved(), "Expired grenade must detonate and remove itself");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void bombCartActivatorAndPersistence(TestContext c) {
    BombCartEntity cart = new BombCartEntity(DeliveryContent.BOMB_CART, c.getWorld());
    cart.setPayload(PayloadType.ATTRACTIVE);
    cart.onActivatorRail(0, 0, 0, true);
    check(
        c,
        cart.isPrimed() && cart.getFuseTicks() == 120,
        "Activator rail must prime with payload fuse");
    NbtCompound data = new NbtCompound();
    cart.writeCustomDataToNbt(data);
    BombCartEntity restored = new BombCartEntity(DeliveryContent.BOMB_CART, c.getWorld());
    restored.readCustomDataFromNbt(data);
    check(
        c,
        restored.getPayload() == PayloadType.ATTRACTIVE && restored.getFuseTicks() == 120,
        "Cart payload and fuse must persist");
    check(
        c,
        restored.getPickBlockStack().isOf(DeliveryContent.BOMB_CARTS.get(PayloadType.ATTRACTIVE)),
        "Cart item must retain payload");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void launcherConsumesAmmoAndEnforcesCooldown(TestContext c) {
    var player = c.createMockPlayer();
    player.getAbilities().creativeMode = false;
    var pos = c.getAbsolutePos(new BlockPos(2, 3, 2));
    player.setPosition(pos.getX(), pos.getY(), pos.getZ());
    ItemStack launcher = new ItemStack(DeliveryContent.ROCKET_LAUNCHER);
    ItemStack ammo = new ItemStack(IcbmPort.MISSILE_ITEMS.get(PayloadType.CONDENSED), 2);
    player.getInventory().setStack(0, ammo);
    var item = (RocketLauncherItem) DeliveryContent.ROCKET_LAUNCHER;
    item.onStoppedUsing(launcher, c.getWorld(), player, 71980);
    check(
        c,
        ammo.getCount() == 1 && launcher.getDamage() == 1,
        "One shot must consume one missile and one durability");
    check(
        c,
        player.getItemCooldownManager().isCoolingDown(item),
        "Launcher must begin server cooldown");
    item.onStoppedUsing(launcher, c.getWorld(), player, 71980);
    check(
        c,
        ammo.getCount() == 1 && launcher.getDamage() == 1,
        "Cooldown must prevent repeated fire");
    c.getWorld()
        .getEntitiesByType(
            IcbmPort.MISSILE_ENTITY, new net.minecraft.util.math.Box(pos).expand(4), e -> true)
        .forEach(net.minecraft.entity.Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void clusterBurstIsBoundedAndPreservesPayload(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 6, 2));
    int count =
        DeliveryContent.burst(
            c.getWorld(),
            net.minecraft.util.math.Vec3d.ofCenter(pos),
            PayloadType.SMOKE,
            null,
            net.minecraft.util.math.Vec3d.ZERO,
            9999);
    var bomblets =
        c.getWorld()
            .getEntitiesByType(
                DeliveryContent.BOMBLET, new net.minecraft.util.math.Box(pos).expand(4), e -> true);
    check(c, count == 64 && bomblets.size() == 64, "Cluster burst must cap entity creation at64");
    check(
        c,
        bomblets.stream().allMatch(b -> b.getPayload() == PayloadType.SMOKE),
        "Every bomblet must inherit its payload");
    bomblets.forEach(net.minecraft.entity.Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void cargoSurvivesReloadAndReleasesOnce(TestContext c) {
    CargoEntity carrier = new CargoEntity(DeliveryContent.CARGO, c.getWorld());
    carrier.setCargo(new ItemStack(net.minecraft.item.Items.DIAMOND, 7));
    carrier.setBalloon(true);
    NbtCompound nbt = new NbtCompound();
    carrier.writeCustomDataToNbt(nbt);
    CargoEntity restored = new CargoEntity(DeliveryContent.CARGO, c.getWorld());
    restored.readCustomDataFromNbt(nbt);
    check(
        c,
        restored.getCargo().isOf(net.minecraft.item.Items.DIAMOND)
            && restored.getCargo().getCount() == 7,
        "Cargo stack must survive reload");
    var pos = c.getAbsolutePos(new BlockPos(2, 4, 2));
    restored.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
    restored.releaseCargo();
    restored.releaseCargo();
    var drops =
        c.getWorld()
            .getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(2),
                e -> e.getStack().isOf(net.minecraft.item.Items.DIAMOND));
    check(
        c,
        drops.size() == 1 && drops.get(0).getStack().getCount() == 7,
        "Carrier release must never duplicate cargo");
    drops.forEach(net.minecraft.entity.Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void detonatingCartCannotAlsoDropArmedItem(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 4, 2));
    BombCartEntity hot = new BombCartEntity(DeliveryContent.BOMB_CART, c.getWorld());
    hot.setPayload(PayloadType.SMOKE);
    hot.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
    hot.dropItems(net.minecraft.entity.damage.DamageSource.IN_FIRE);
    var drops =
        c.getWorld()
            .getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(2),
                e -> e.getStack().isOf(DeliveryContent.BOMB_CARTS.get(PayloadType.SMOKE)));
    check(
        c,
        hot.isRemoved() && drops.isEmpty(),
        "Detonating cart must never return an armed cart item");
    BombCartEntity primed = new BombCartEntity(DeliveryContent.BOMB_CART, c.getWorld());
    primed.setPayload(PayloadType.SMOKE);
    primed.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
    primed.prime();
    primed.dropItems(net.minecraft.entity.damage.DamageSource.GENERIC);
    drops =
        c.getWorld()
            .getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(2),
                e -> e.getStack().isOf(DeliveryContent.BOMB_CARTS.get(PayloadType.SMOKE)));
    check(c, drops.isEmpty(), "Destroying a primed cart must never recover its active payload");
    BombCartEntity cold = new BombCartEntity(DeliveryContent.BOMB_CART, c.getWorld());
    cold.setPayload(PayloadType.SMOKE);
    cold.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
    cold.dropItems(net.minecraft.entity.damage.DamageSource.GENERIC);
    drops =
        c.getWorld()
            .getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(2),
                e -> e.getStack().isOf(DeliveryContent.BOMB_CARTS.get(PayloadType.SMOKE)));
    check(c, drops.size() == 1, "Inert stationary cart must remain recoverable");
    drops.forEach(net.minecraft.entity.Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void mixedClusterCraftingPreservesAndLimitsContents(TestContext c) {
    var player = c.createMockPlayer();
    var handler = new net.minecraft.screen.CraftingScreenHandler(0, player.getInventory());
    var inventory = new net.minecraft.inventory.CraftingInventory(handler, 3, 3);
    var recipe = new ClusterLoadingRecipe(IcbmPort.id("test_cluster_loading"));
    ItemStack cluster = new ItemStack(DeliveryContent.CLUSTER_MISSILE);
    new ClusterPlan(java.util.List.of(PayloadType.INCENDIARY)).writeTo(cluster);
    inventory.setStack(0, cluster);
    inventory.setStack(1, new ItemStack(DeliveryContent.BOMBLETS.get(PayloadType.SMOKE), 16));
    inventory.setStack(2, new ItemStack(DeliveryContent.BOMBLETS.get(PayloadType.SHRAPNEL), 16));
    ItemStack output = recipe.craft(inventory);
    check(
        c,
        ClusterPlan.fromStack(output)
            .payloads()
            .equals(
                java.util.List.of(PayloadType.INCENDIARY, PayloadType.SMOKE, PayloadType.SHRAPNEL)),
        "Mixed loading must append exactly one bomblet per slot");
    check(c, ClusterPlan.fromStack(cluster).size() == 1, "Crafting preview must not mutate input");
    new ClusterPlan(java.util.Collections.nCopies(64, PayloadType.SMOKE)).writeTo(cluster);
    check(
        c,
        !recipe.matches(inventory, c.getWorld()),
        "Overfilled cluster recipes must fail rather than consume excess payload");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void heldMissileCraftingConservesItemAndCasing(TestContext c) {
    var player = c.createMockPlayer();
    var handler = new net.minecraft.screen.CraftingScreenHandler(0, player.getInventory());
    var inventory = new net.minecraft.inventory.CraftingInventory(handler, 3, 3);
    var loading = new HeldItemLoadingRecipe(IcbmPort.id("test_held_load"));
    var unloading = new HeldItemUnloadingRecipe(IcbmPort.id("test_held_unload"));
    ItemStack casing = new ItemStack(DeliveryContent.HELD_ITEM_MISSILE);
    ItemStack diamonds = new ItemStack(net.minecraft.item.Items.DIAMOND, 16);
    diamonds.setCustomName(net.minecraft.text.Text.literal("Cargo marker"));
    inventory.setStack(0, casing);
    inventory.setStack(1, diamonds);
    ItemStack loaded = loading.craft(inventory);
    check(
        c,
        HeldItemMissileItem.cargo(loaded).getCount() == 1
            && HeldItemMissileItem.cargo(loaded).hasCustomName(),
        "Loading must copy exactly one item with NBT");
    check(
        c,
        HeldItemMissileItem.cargo(casing).isEmpty() && diamonds.getCount() == 16,
        "Crafting preview must not mutate inputs");
    inventory.setStack(0, loaded);
    check(c, loading.craft(inventory).isEmpty(), "Loaded missiles must reject cargo overwrite");
    inventory.setStack(1, ItemStack.EMPTY);
    check(
        c,
        unloading.craft(inventory).getCount() == 1,
        "Unloading must recover exactly one cargo item");
    var remainder = unloading.getRemainder(inventory).get(0);
    check(
        c,
        remainder.isOf(DeliveryContent.HELD_ITEM_MISSILE)
            && remainder.getCount() == 1
            && HeldItemMissileItem.cargo(remainder).isEmpty(),
        "Unloading must return exactly one empty casing");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void heldBlockPlacementConsumesCargoAndRecoversCasingOnce(TestContext c) {
    var pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    c.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState());
    var player = c.createMockPlayer();
    player.getAbilities().allowModifyWorld = true;
    ItemStack loaded = new ItemStack(DeliveryContent.HELD_ITEM_MISSILE);
    HeldItemMissileItem.setCargo(loaded, new ItemStack(net.minecraft.item.Items.DIRT));
    loaded.getOrCreateNbt().putString("HeldAction", "SECONDARY");
    var missile = new HeldItemMissileEntity(DeliveryContent.HELD_MISSILE, c.getWorld());
    missile.setMissile(loaded);
    missile.setOwner(player);
    missile.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos.up()));
    missile.setVelocity(0, -1, 0);
    missile.impact(
        new net.minecraft.util.hit.BlockHitResult(
            net.minecraft.util.math.Vec3d.ofCenter(pos).add(0, .5, 0),
            net.minecraft.util.math.Direction.UP,
            pos,
            false));
    missile.recover();
    check(
        c,
        c.getWorld().getBlockState(pos.up()).isOf(Blocks.DIRT),
        "Secondary impact must place held block");
    var drops =
        c.getWorld()
            .getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(3),
                e -> e.getStack().isOf(DeliveryContent.HELD_ITEM_MISSILE));
    check(
        c,
        drops.size() == 1 && HeldItemMissileItem.cargo(drops.get(0).getStack()).isEmpty(),
        "Consumed block must not remain in recovered missile");
    drops.forEach(net.minecraft.entity.Entity::discard);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void heldMissileCargoPersistsThroughEntityReload(TestContext c) {
    var stack = new ItemStack(DeliveryContent.HELD_ITEM_MISSILE);
    var sword = new ItemStack(net.minecraft.item.Items.DIAMOND_SWORD);
    sword.setDamage(12);
    HeldItemMissileItem.setCargo(stack, sword);
    var missile = new HeldItemMissileEntity(DeliveryContent.HELD_MISSILE, c.getWorld());
    missile.setMissile(stack);
    NbtCompound nbt = new NbtCompound();
    missile.writeCustomDataToNbt(nbt);
    var restored = new HeldItemMissileEntity(DeliveryContent.HELD_MISSILE, c.getWorld());
    restored.readCustomDataFromNbt(nbt);
    check(
        c,
        HeldItemMissileItem.cargo(restored.getMissile())
                .isOf(net.minecraft.item.Items.DIAMOND_SWORD)
            && HeldItemMissileItem.cargo(restored.getMissile()).getDamage() == 12,
        "Held item identity and durability must survive reload");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void ballisticHandheldRequiresDistantTargetAndUsesArc(TestContext c) {
    var player = c.createMockPlayer();
    player.getAbilities().creativeMode = false;
    // Keep the thirty-block sight line above neighboring concurrently running structures.
    var pos = c.getAbsolutePos(new BlockPos(2, 3, 2)).withY(c.getWorld().getTopY() - 40);
    player.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos));
    player.setYaw(0);
    // LivingEntity.getYaw(float), used by raycast, reads headYaw rather than body yaw.
    player.setHeadYaw(0);
    player.setBodyYaw(0);
    player.setPitch(0);
    var launcher = new ItemStack(DeliveryContent.BALLISTIC_LAUNCHER);
    var ammo = new ItemStack(IcbmPort.CONDENSED_MISSILE, 2);
    player.getInventory().setStack(0, ammo);
    var near = new net.minecraft.util.math.BlockPos(player.getEyePos().add(0, 0, 5));
    var far = new net.minecraft.util.math.BlockPos(player.getEyePos().add(0, 0, 30));
    c.getWorld().setBlockState(near, Blocks.STONE.getDefaultState());
    ((RocketLauncherItem) DeliveryContent.BALLISTIC_LAUNCHER)
        .onStoppedUsing(launcher, c.getWorld(), player, 71980);
    check(
        c,
        ammo.getCount() == 2 && launcher.getDamage() == 0,
        "Nearby target must preserve ammo and durability");
    c.getWorld().removeBlock(near, false);
    c.getWorld().setBlockState(far, Blocks.STONE.getDefaultState());
    var sight = player.raycast(500, 1, false);
    check(
        c,
        sight instanceof net.minecraft.util.hit.BlockHitResult hit
            && sight.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
            && hit.getBlockPos().equals(far),
        "Isolated ballistic sight line must reach the distant fixture; hit="
            + sight.getType()
            + " at "
            + sight.getPos()
            + ", expected="
            + far
            + ", player="
            + player.getPos()
            + ", eye="
            + player.getEyePos()
            + ", camera="
            + player.getCameraPosVec(1)
            + ", rotation="
            + player.getRotationVec(1));
    ((RocketLauncherItem) DeliveryContent.BALLISTIC_LAUNCHER)
        .onStoppedUsing(launcher, c.getWorld(), player, 71980);
    var missiles =
        c.getWorld()
            .getEntitiesByType(
                IcbmPort.MISSILE_ENTITY, new net.minecraft.util.math.Box(pos).expand(4), e -> true);
    check(
        c,
        ammo.getCount() == 1 && missiles.size() == 1,
        "Distant target must launch exactly one missile; remaining ammo="
            + ammo.getCount()
            + ", nearby missiles="
            + missiles.size());
    NbtCompound flight = new NbtCompound();
    missiles.get(0).writeCustomDataToNbt(flight);
    check(
        c,
        flight.getCompound("FlightPlan").contains("Target")
            && flight.getCompound("FlightPlan").getLong("Target") == far.asLong(),
        "Ballistic launcher must configure target arc");
    missiles.forEach(net.minecraft.entity.Entity::discard);
    c.getWorld().removeBlock(far, false);
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void carrierCraftingTransfersOneItemAndPreservesModes(TestContext c) {
    var player = c.createMockPlayer();
    var handler = new net.minecraft.screen.CraftingScreenHandler(0, player.getInventory());
    var inventory = new net.minecraft.inventory.CraftingInventory(handler, 3, 3);
    var loading = new CarrierLoadingRecipe(IcbmPort.id("carrier_test_load"));
    var unloading = new CarrierUnloadingRecipe(IcbmPort.id("carrier_test_unload"));
    ItemStack carrier = new ItemStack(DeliveryContent.CARGO_PARACHUTE);
    carrier.getOrCreateNbt().putString("CarrierMode", "BLOCK");
    ItemStack chest = new ItemStack(net.minecraft.item.Items.CHEST, 16);
    chest.getOrCreateNbt().putString("Marker", "preserve");
    inventory.setStack(0, carrier);
    inventory.setStack(1, chest);
    ItemStack loaded = loading.craft(inventory);
    check(
        c,
        CarrierData.cargo(loaded).getCount() == 1
            && CarrierData.cargo(loaded).getNbt().getString("Marker").equals("preserve")
            && CarrierData.mode(loaded) == CarrierData.Mode.BLOCK,
        "Carrier loading must preserve one item and mode");
    inventory.setStack(0, loaded);
    check(c, loading.craft(inventory).isEmpty(), "Loaded carrier cannot overwrite cargo");
    inventory.setStack(1, ItemStack.EMPTY);
    check(
        c,
        unloading.craft(inventory).getCount() == 1
            && CarrierData.cargo(unloading.getRemainder(inventory).get(0)).isEmpty(),
        "Carrier unload must return one item and one empty carrier");
    check(
        c,
        chest.getCount() == 16 && CarrierData.cargo(carrier).isEmpty(),
        "Crafting previews must not mutate source inventories");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void carrierPassengerTreeSurvivesSaveAndReleasesWithoutDuplicate(TestContext c) {
    var carrier = new CargoEntity(DeliveryContent.CARGO, c.getWorld());
    carrier.setPosition(
        net.minecraft.util.math.Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 8, 2))));
    carrier.setCargo(new ItemStack(net.minecraft.item.Items.DIAMOND, 3));
    c.getWorld().spawnEntity(carrier);
    check(
        c,
        carrier.deployCargo() && carrier.hasPassengers() && carrier.getCargo().isEmpty(),
        "Cargo deployment must transfer ownership to a real passenger");
    NbtCompound nbt = new NbtCompound();
    carrier.saveNbt(nbt);
    var originalPassenger = carrier.getFirstPassenger();
    originalPassenger.stopRiding();
    originalPassenger.discard();
    carrier.discard();
    var restored =
        (CargoEntity)
            net.minecraft.entity.EntityType.loadEntityWithPassengers(nbt, c.getWorld(), e -> e);
    check(
        c,
        restored != null
            && restored.getFirstPassenger() instanceof net.minecraft.entity.ItemEntity item
            && item.getStack().getCount() == 3,
        "Vanilla passenger tree must restore cargo exactly once");
    var passenger = restored.getFirstPassenger();
    restored.releaseCargo();
    restored.releaseCargo();
    check(
        c,
        !passenger.hasVehicle() && restored.getCargo().isEmpty(),
        "Release must dismount passenger without recreating stored cargo");
    passenger.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void carrierBlockModeUsesPersistentFlyingBlockPassenger(TestContext c) {
    var carrier = new CargoEntity(DeliveryContent.CARGO, c.getWorld());
    carrier.setMode(CarrierData.Mode.BLOCK);
    carrier.setCargo(new ItemStack(net.minecraft.item.Items.CHEST));
    carrier.setPosition(
        net.minecraft.util.math.Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 8, 2))));
    c.getWorld().spawnEntity(carrier);
    check(
        c,
        carrier.deployCargo()
            && carrier.getFirstPassenger() instanceof dev.icbm.port.simulation.FlyingBlockEntity,
        "Block mode must attach a flying block entity");
    var passenger = carrier.getFirstPassenger();
    carrier.releaseCargo();
    check(
        c,
        !passenger.hasVehicle(),
        "Releasing block cargo must resume its independent falling behavior");
    passenger.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void heldSecondaryUsesVanillaShearsAndRestoresPlayerHand(TestContext c) {
    var player = c.createMockPlayer();
    player.setStackInHand(
        net.minecraft.util.Hand.MAIN_HAND, new ItemStack(net.minecraft.item.Items.STICK));
    var sheep = net.minecraft.entity.EntityType.SHEEP.create(c.getWorld());
    sheep.setPosition(
        net.minecraft.util.math.Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2))));
    c.getWorld().spawnEntity(sheep);
    ItemStack missileStack = new ItemStack(DeliveryContent.HELD_ITEM_MISSILE);
    HeldItemMissileItem.setCargo(missileStack, new ItemStack(net.minecraft.item.Items.SHEARS));
    missileStack.getOrCreateNbt().putString("HeldAction", "SECONDARY");
    var missile = new HeldItemMissileEntity(DeliveryContent.HELD_MISSILE, c.getWorld());
    missile.setOwner(player);
    missile.setMissile(missileStack);
    missile.setPosition(sheep.getPos());
    missile.impact(new net.minecraft.util.hit.EntityHitResult(sheep));
    check(
        c,
        sheep.isSheared() && player.getMainHandStack().isOf(net.minecraft.item.Items.STICK),
        "Held secondary action must use vanilla shears and restore owner's real hand");
    sheep.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void deliveryCatalogMatchesUpstreamTiers(TestContext c) {
    for (PayloadType p : PayloadType.values()) {
      check(
          c,
          DeliveryContent.BOMB_CARTS.containsKey(p) == p.hasContent(),
          "Cart registration mismatch: " + p);
      check(
          c,
          DeliveryContent.GRENADES.containsKey(p) == (p.hasContent() && p.tier() == 1),
          "Grenade registration mismatch: " + p);
    }
    c.complete();
  }
}
