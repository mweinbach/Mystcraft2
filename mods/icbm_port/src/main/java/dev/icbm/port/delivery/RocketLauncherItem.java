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

/** Inventory missile selection on release, with server-side cooldown and durability cost. */
public final class RocketLauncherItem extends Item {
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
        .isEmpty()) return;
    net.minecraft.util.math.BlockPos target = null;
    if (ballistic) {
      var hit = player.raycast(500, 1, false);
      if (!(hit instanceof net.minecraft.util.hit.BlockHitResult blockHit)
          || hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK
          || player.getPos().distanceTo(hit.getPos()) < 20) return;
      target = blockHit.getBlockPos();
    }
    boolean launched;
    if (DeliveryContent.isLoadedHeldMissile(ammo))
      launched =
          DeliveryContent.launchHeld(
              world, player, ammo, muzzle, direction.multiply(IcbmPort.MISSILE_SPEED), target);
    else {
      MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
      missile.setOwner(player);
      missile.setPayload(payload);
      missile.setCluster(ammo.isOf(DeliveryContent.CLUSTER_MISSILE));
      if (ammo.isOf(DeliveryContent.CLUSTER_MISSILE))
        missile.setClusterPlan(ClusterPlan.fromStack(ammo));
      missile.setPosition(muzzle);
      missile.setVelocity(direction.multiply(IcbmPort.MISSILE_SPEED));
      if (target != null) {
        try {
          missile.configureTarget(target);
        } catch (IllegalArgumentException ignored) {
          return;
        }
      }
      launched = world.spawnEntity(missile);
    }
    if (!launched) return;
    if (!player.getAbilities().creativeMode) {
      ammo.decrement(1);
      stack.damage(1, player, p -> p.sendToolBreakStatus(player.getActiveHand()));
    }
    player.getItemCooldownManager().set(this, 20);
    world.playSound(
        null,
        player.getBlockPos(),
        SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
        SoundCategory.PLAYERS,
        1,
        1);
  }
}
