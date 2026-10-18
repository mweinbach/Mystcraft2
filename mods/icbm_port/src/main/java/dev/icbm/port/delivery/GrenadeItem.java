package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.payload.PayloadType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Hold up to three seconds to scale throwing velocity, as in ItemGrenade. */
public final class GrenadeItem extends Item {
  private final PayloadType payload;

  public GrenadeItem(PayloadType payload) {
    super(new Settings().group(IcbmPort.GROUP).maxCount(16));
    this.payload = payload;
  }

  @Override
  public int getMaxUseTime(ItemStack stack) {
    return 60;
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

  @Override
  public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
    if (world.isClient) return;
    GrenadeEntity grenade = new GrenadeEntity(DeliveryContent.GRENADE, world);
    grenade.setOwner(user);
    grenade.setPayload(payload);
    grenade.setItem(stack);
    grenade.setPosition(user.getX(), user.getEyeY() - .1, user.getZ());
    grenade.setVelocity(
        user.getRotationVec(1).multiply(1.8 * Math.max(0, Math.min(60, 60 - remaining)) / 60.0));
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
