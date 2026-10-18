package dev.icbm.port.defense;

import dev.icbm.port.IcbmPort;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Upstream antidote clears all potion effects after a 32-tick eating action. */
public final class AntidoteItem extends Item {
  AntidoteItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(16));
  }

  @Override
  public int getMaxUseTime(ItemStack stack) {
    return 32;
  }

  @Override
  public UseAction getUseAction(ItemStack stack) {
    return UseAction.EAT;
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    return ItemUsage.consumeHeldItem(world, player, hand);
  }

  @Override
  public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
    if (!world.isClient) {
      user.clearStatusEffects();
      dev.icbm.port.exposure.ExposureContent.clear(user);
      if (!(user instanceof PlayerEntity player) || !player.getAbilities().creativeMode)
        stack.decrement(1);
    }
    return stack;
  }
}
