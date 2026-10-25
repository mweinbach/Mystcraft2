package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.payload.PayloadType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.*;
import net.minecraft.world.World;

/**
 * Hold up to three seconds to scale the throw, adapted from MIT ICBM-Classic {@code
 * content/items/ItemGrenade.java}: stacks of 16, BOW use action, primed-TNT throw sound, one grenade
 * consumed outside creative mode.
 *
 * <p>Deliberate departure: upstream never throws when the full 60-tick use completes (1.12 calls
 * {@code onItemUseFinish}, not {@code onPlayerStoppedUsing}); the port throws at full charge.
 */
public final class GrenadeItem extends Item {
  public static final int MAX_USE_TICKS = 60;
  private final PayloadType payload;

  public GrenadeItem(PayloadType payload) {
    super(new Settings().group(IcbmPort.GROUP).maxCount(16));
    this.payload = payload;
  }

  public PayloadType getPayload() {
    return payload;
  }

  @Override
  public int getMaxUseTime(ItemStack stack) {
    return MAX_USE_TICKS;
  }

  @Override
  public UseAction getUseAction(ItemStack stack) {
    return UseAction.BOW;
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    player.setCurrentHand(hand);
    return TypedActionResult.consume(player.getStackInHand(hand));
  }

  /** Upstream throw energy: fraction of the three-second use that was held. */
  public static float charge(int remainingUseTicks) {
    return Math.max(0, Math.min(MAX_USE_TICKS, MAX_USE_TICKS - remainingUseTicks))
        / (float) MAX_USE_TICKS;
  }

  @Override
  public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
    if (world.isClient) return;
    world.playSound(
        null,
        user.getX(),
        user.getY(),
        user.getZ(),
        SoundEvents.ENTITY_TNT_PRIMED,
        SoundCategory.BLOCKS,
        0.5F,
        0.4F / (world.random.nextFloat() * 0.4F + 0.8F));
    GrenadeEntity grenade = new GrenadeEntity(DeliveryContent.GRENADE, world);
    grenade.setOwner(user);
    grenade.setPayload(payload);
    grenade.setItem(stack);
    grenade.aimFromThrower(user, charge(remaining));
    if (world.spawnEntity(grenade)
        && (!(user instanceof PlayerEntity player) || !player.getAbilities().creativeMode))
      stack.decrement(1);
  }

  @Override
  public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
    onStoppedUsing(stack, world, user, 0);
    return stack;
  }
}
