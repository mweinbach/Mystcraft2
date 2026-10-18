package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Item-only cargo attachment. Upstream passenger/flying-block attachment is not ported. */
public final class CargoCarrierItem extends Item {
  private final boolean balloon;

  public CargoCarrierItem(boolean balloon) {
    super(new Settings().group(IcbmPort.GROUP).maxCount(16));
    this.balloon = balloon;
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    Hand cargoHand = hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
    ItemStack cargo = player.getStackInHand(cargoHand);
    if (cargo.isEmpty()) return TypedActionResult.fail(stack);
    if (!world.isClient) {
      CargoEntity entity = new CargoEntity(DeliveryContent.CARGO, world);
      entity.setBalloon(balloon);
      entity.setCargo(cargo.copy());
      entity.setItem(stack);
      entity.setPosition(player.getEyePos().add(player.getRotationVec(1)));
      entity.setVelocity(player.getRotationVec(1).multiply(.4));
      if (world.spawnEntity(entity)) {
        if (!player.getAbilities().creativeMode) {
          cargo.setCount(0);
          stack.decrement(1);
        }
      }
    }
    return TypedActionResult.success(stack, world.isClient);
  }
}
