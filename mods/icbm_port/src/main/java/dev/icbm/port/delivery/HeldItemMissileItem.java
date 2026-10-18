package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Craft in one held item. Sneak-use cycles upstream PRIMARY/SECONDARY action order. */
public final class HeldItemMissileItem extends Item {
  public enum Mode {
    PRIMARY,
    SECONDARY,
    PRIMARY_FIRST,
    SECONDARY_FIRST
  }

  public HeldItemMissileItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(1));
  }

  public static ItemStack cargo(ItemStack missile) {
    ItemStack cargo =
        missile.hasNbt()
            ? ItemStack.fromNbt(missile.getNbt().getCompound("HeldItem"))
            : ItemStack.EMPTY;
    if (!cargo.isEmpty()) cargo.setCount(1);
    return cargo;
  }

  public static void setCargo(ItemStack missile, ItemStack cargo) {
    ItemStack single = cargo.copy();
    if (!single.isEmpty()) single.setCount(1);
    missile.getOrCreateNbt().put("HeldItem", single.writeNbt(new net.minecraft.nbt.NbtCompound()));
  }

  public static Mode mode(ItemStack missile) {
    try {
      return Mode.valueOf(missile.hasNbt() ? missile.getNbt().getString("HeldAction") : "");
    } catch (IllegalArgumentException ignored) {
      return Mode.PRIMARY_FIRST;
    }
  }

  public static boolean allowed(ItemStack cargo) {
    return !cargo.isEmpty()
        && !cargo.isOf(DeliveryContent.HELD_ITEM_MISSILE)
        && !cargo.isOf(DeliveryContent.CLUSTER_MISSILE)
        && !cargo.isOf(DeliveryContent.CARGO_BALLOON)
        && !cargo.isOf(DeliveryContent.CARGO_PARACHUTE)
        && IcbmPort.payloadForMissile(cargo) == null;
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    if (!player.isSneaking()) return TypedActionResult.pass(stack);
    if (!world.isClient) {
      Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
      stack.getOrCreateNbt().putString("HeldAction", next.name());
      player.sendMessage(Text.translatable("tooltip.icbm_port.held_mode", next.name()), true);
    }
    return TypedActionResult.success(stack, world.isClient);
  }

  @Override
  public void appendTooltip(
      ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
    ItemStack cargo = cargo(stack);
    tooltip.add(
        Text.translatable(
            "tooltip.icbm_port.held_contents",
            cargo.isEmpty() ? Text.literal("Empty") : cargo.getName()));
    tooltip.add(Text.translatable("tooltip.icbm_port.held_mode", mode(stack).name()));
  }
}
