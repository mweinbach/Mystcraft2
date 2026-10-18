package dev.icbm.port.delivery;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.*;
import net.minecraft.world.World;

/** A scoped real-owner item context; original held equipment is restored even if a mod throws. */
final class PlayerOwnedItemActions {
  private static final class Context extends ItemUsageContext {
    Context(World world, PlayerEntity player, ItemStack stack, BlockHitResult hit) {
      super(world, player, Hand.MAIN_HAND, stack, hit);
    }
  }

  static ItemStack secondary(PlayerEntity player, ItemStack held, HitResult hit) {
    return apply(player, held, hit, false);
  }

  static ItemStack attack(PlayerEntity player, ItemStack held, HitResult hit) {
    return apply(player, held, hit, true);
  }

  private static ItemStack apply(
      PlayerEntity player, ItemStack held, HitResult hit, boolean primary) {
    ItemStack original = player.getMainHandStack();
    ItemStack result = held;
    player.setStackInHand(Hand.MAIN_HAND, held.copy());
    try {
      if (hit instanceof EntityHitResult entityHit) {
        if (primary) player.attack(entityHit.getEntity());
        else player.interact(entityHit.getEntity(), Hand.MAIN_HAND);
      } else if (!primary && hit instanceof BlockHitResult blockHit) {
        player
            .getMainHandStack()
            .useOnBlock(new Context(player.world, player, player.getMainHandStack(), blockHit));
      }
    } catch (RuntimeException error) {
      org.slf4j.LoggerFactory.getLogger("icbm_port")
          .warn("Held item interaction failed; recovering remaining item", error);
    } finally {
      result = player.getMainHandStack().copy();
      player.setStackInHand(Hand.MAIN_HAND, original);
    }
    return result;
  }

  private PlayerOwnedItemActions() {}
}
