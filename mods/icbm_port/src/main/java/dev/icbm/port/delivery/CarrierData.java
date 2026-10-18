package dev.icbm.port.delivery;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Source CargoProjectileData's held-item and ITEM/BLOCK/ENTITY/PROJECTILE modes. */
public final class CarrierData {
  public enum Mode {
    ITEM,
    BLOCK,
    ENTITY,
    PROJECTILE
  }

  public static boolean isCarrier(ItemStack stack) {
    return stack.isOf(DeliveryContent.CARGO_PARACHUTE) || stack.isOf(DeliveryContent.CARGO_BALLOON);
  }

  public static ItemStack cargo(ItemStack stack) {
    return stack.hasNbt()
        ? ItemStack.fromNbt(stack.getNbt().getCompound("CarrierCargo"))
        : ItemStack.EMPTY;
  }

  public static void setCargo(ItemStack holder, ItemStack cargo) {
    ItemStack single = cargo.copy();
    if (!single.isEmpty()) single.setCount(1);
    holder.getOrCreateNbt().put("CarrierCargo", single.writeNbt(new NbtCompound()));
  }

  public static Mode mode(ItemStack holder) {
    try {
      return Mode.valueOf(holder.hasNbt() ? holder.getNbt().getString("CarrierMode") : "");
    } catch (IllegalArgumentException ignored) {
      return Mode.ITEM;
    }
  }

  public static boolean allowed(ItemStack cargo) {
    return DeliveryConfig.cargoAllowed(cargo)
        && !isCarrier(cargo)
        && !cargo.isOf(DeliveryContent.CLUSTER_MISSILE)
        && !cargo.isOf(DeliveryContent.HELD_ITEM_MISSILE);
  }

  private CarrierData() {}
}
