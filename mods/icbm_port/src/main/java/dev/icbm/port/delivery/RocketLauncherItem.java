package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.payload.PayloadType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.sound.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

/**
 * Inventory missile selection on release with a server-side cooldown.
 *
 * <p>Adapted from MIT ICBM-Classic {@code content/items/ItemRocketLauncher.java}: the first
 * loaded missile in the inventory is fired, the ballistic variant needs a block target at least
 * {@link #MIN_DISTANCE} away and raises cluster aim points by {@link #BALLISTIC_BURST_Y}, failures
 * report an action-bar message, and the launcher has no wear (upstream's electrical item never
 * runs out of charge). The 20-tick item cooldown stands in for upstream's 1000 ms click delay.
 * Cluster ammo flies as an {@code icbm_port:cluster} actionable missile; sneaking rides the rocket
 * and a leashed mob is seated when a lead is held in the off hand.
 */
public final class RocketLauncherItem extends Item {
  public static final double MIN_DISTANCE = 20;
  public static final int BALLISTIC_BURST_Y =
      (int) dev.icbm.port.entity.ActionableMissileEntity.BALLISTIC_BURST_HEIGHT;
  public static final int COOLDOWN_TICKS = 20;
  /**
   * Upstream raytraces 500 blocks, but a direct (non-ballistic) rocket covers only {@code
   * MISSILE_LIFETIME * MISSILE_SPEED} blocks (300) from the muzzle, one block ahead of the eye.
   */
  public static final double DIRECT_CLUSTER_AIM_RANGE =
      Math.min(500, IcbmPort.MISSILE_LIFETIME * IcbmPort.MISSILE_SPEED);
  private final boolean ballistic;

  public RocketLauncherItem(Settings settings) {
    this(settings, false);
  }

  public RocketLauncherItem(Settings settings, boolean ballistic) {
    super(settings);
    this.ballistic = ballistic;
  }

  public static ItemStack findAmmo(PlayerEntity player) {
    for (int slot = 0; slot < player.getInventory().size(); slot++) {
      ItemStack stack = player.getInventory().getStack(slot);
      if (DeliveryContent.isLoadedHeldMissile(stack)
          || (!stack.isEmpty()
              && IcbmPort.payloadForMissile(stack) != null
              && (!stack.isOf(DeliveryContent.CLUSTER_MISSILE)
                  || !ClusterPlan.fromStack(stack).isEmpty()))) return stack;
    }
    return ItemStack.EMPTY;
  }

  @Override
  public int getMaxUseTime(ItemStack stack) {
    return 72000;
  }

  @Override
  public UseAction getUseAction(ItemStack stack) {
    return UseAction.BOW;
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    if (player.getItemCooldownManager().isCoolingDown(this) || findAmmo(player).isEmpty())
      return TypedActionResult.fail(stack);
    player.setCurrentHand(hand);
    return TypedActionResult.consume(stack);
  }

  private static void status(PlayerEntity player, String key, Object... args) {
    player.sendMessage(net.minecraft.text.Text.translatable(key, args), true);
  }

  @Override
  public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
    if (world.isClient
        || !(user instanceof PlayerEntity player)
        || player.getItemCooldownManager().isCoolingDown(this)) return;
    ItemStack ammo = findAmmo(player);
    PayloadType payload = IcbmPort.payloadForMissile(ammo);
    if (payload == null && !DeliveryContent.isLoadedHeldMissile(ammo)) return;
    var direction = player.getRotationVec(1);
    var muzzle = player.getEyePos().add(direction.multiply(1));
    if (!world
        .getBlockState(new net.minecraft.util.math.BlockPos(muzzle))
        .getCollisionShape(world, new net.minecraft.util.math.BlockPos(muzzle))
        .isEmpty()) {
      status(player, "message.icbm_port.rocket_launcher.blocked");
      return;
    }
    net.minecraft.util.math.BlockPos target = null;
    if (ballistic) {
      var hit = player.raycast(500, 1, false);
      if (!(hit instanceof net.minecraft.util.hit.BlockHitResult blockHit)
          || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) {
        status(player, "message.icbm_port.rocket_launcher.no_target");
        return;
      }
      if (player.getPos().distanceTo(hit.getPos()) < MIN_DISTANCE) {
        status(player, "message.icbm_port.rocket_launcher.too_close", (int) MIN_DISTANCE);
        return;
      }
      target = blockHit.getBlockPos();
    }
    boolean launched = true;
    net.minecraft.entity.Entity rocket = null;
    if (DeliveryContent.isLoadedHeldMissile(ammo)) {
      rocket =
          DeliveryContent.launchHeldEntity(
              world, player, ammo, muzzle, direction.multiply(IcbmPort.MISSILE_SPEED), target);
      launched = rocket != null;
    } else {
      boolean clusterAmmo = ammo.isOf(DeliveryContent.CLUSTER_MISSILE);
      MissileEntity missile =
          clusterAmmo
              ? dev.icbm.port.entity.ActionableMissileEntity.forCluster(world, ammo)
              : new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
      if (missile == null) return;
      missile.setOwner(player);
      if (!clusterAmmo) missile.setPayload(payload);
      missile.setPosition(muzzle);
      missile.setVelocity(direction.multiply(IcbmPort.MISSILE_SPEED));
      if (missile instanceof dev.icbm.port.entity.ActionableMissileEntity actionable) {
        // Upstream raytraces 500 blocks for an air-burst aim point and lifts ballistic cluster
        // aim by ballisticBurstY; release happens within 3 blocks and never on impact.
        net.minecraft.util.math.Vec3d aim = null;
        if (target != null)
          aim = net.minecraft.util.math.Vec3d.ofCenter(target).add(0, BALLISTIC_BURST_Y, 0);
        else {
          // A direct rocket flies straight and expires after MISSILE_LIFETIME ticks, so only aim
          // points it can reach become proximity targets; beyond that the timed release applies.
          var hit = player.raycast(DIRECT_CLUSTER_AIM_RANGE, 1, false);
          if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) aim = hit.getPos();
        }
        if (aim != null)
          actionable.setProximityTrigger(
              aim, dev.icbm.port.entity.ActionableMissileEntity.CLUSTER_TRIGGER_DISTANCE, false);
      }
      if (target != null) {
        net.minecraft.util.math.BlockPos flightTarget = target;
        if (clusterAmmo) {
          // Upstream moves the cluster's target data itself up by ballisticBurstY.
          var raised = target.up(BALLISTIC_BURST_Y);
          if (!world.isOutOfHeightLimit(raised)) flightTarget = raised;
        }
        try {
          missile.configureTarget(flightTarget);
        } catch (IllegalArgumentException ignored) {
          launched = false;
        }
      }
      launched = launched && world.spawnEntity(missile);
      rocket = missile;
    }
    if (!launched) {
      status(player, "message.icbm_port.rocket_launcher.spawn_failed");
      return;
    }
    if (player.isSneaking()) player.startRiding(rocket, true);
    else if (player.getOffHandStack().isOf(Items.LEAD))
      for (net.minecraft.entity.mob.MobEntity victim :
          world.getEntitiesByClass(
              net.minecraft.entity.mob.MobEntity.class,
              player.getBoundingBox().expand(7),
              mob -> mob.getHoldingEntity() == player)) {
        victim.startRiding(rocket, true);
        break;
      }
    // No launcher wear: upstream's electrical launcher never runs out of charge.
    if (!player.getAbilities().creativeMode) ammo.decrement(1);
    player.getItemCooldownManager().set(this, COOLDOWN_TICKS);
    world.playSound(
        null,
        player.getBlockPos(),
        SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
        SoundCategory.PLAYERS,
        1,
        1);
  }
}
