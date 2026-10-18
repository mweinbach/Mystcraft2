package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** Throwable cargo holder. Sneak-use cycles source cargo modes; use on an entity attaches it. */
public final class CargoCarrierItem extends Item {
  private final boolean balloon;

  public CargoCarrierItem(boolean balloon) {
    super(new Settings().group(IcbmPort.GROUP).maxCount(16));
    this.balloon = balloon;
  }

  private CargoEntity carrier(World world, PlayerEntity player, ItemStack stack) {
    CargoEntity carrier = new CargoEntity(DeliveryContent.CARGO, world);
    carrier.setBalloon(balloon);
    carrier.setOwner(player);
    carrier.setItem(stack);
    carrier.setMode(CarrierData.mode(stack));
    carrier.setPosition(player.getEyePos().add(player.getRotationVec(1)));
    carrier.setVelocity(player.getRotationVec(1).multiply(.5));
    return carrier;
  }

  public ActionResult attachToEntity(PlayerEntity player, Hand hand, Entity target) {
    ItemStack stack = player.getStackInHand(hand);
    if (!CarrierData.cargo(stack).isEmpty()
        || target.hasVehicle()
        || !DeliveryConfig.entityAllowed(target)) return ActionResult.FAIL;
    if (player.world.isClient) return ActionResult.SUCCESS;
    CargoEntity carrier = carrier(player.world, player, stack);
    carrier.setPosition(target.getPos().add(0, target.getHeight() + .7, 0));
    carrier.setVelocity(target.getVelocity());
    if (!player.world.spawnEntity(carrier)) return ActionResult.FAIL;
    if (!carrier.attachPassenger(target)) {
      carrier.discard();
      return ActionResult.FAIL;
    }
    if (!player.getAbilities().creativeMode) stack.decrement(1);
    return ActionResult.CONSUME;
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    if (player.isSneaking()) {
      if (!world.isClient) {
        var next =
            CarrierData.Mode.values()[
                (CarrierData.mode(stack).ordinal() + 1) % CarrierData.Mode.values().length];
        stack.getOrCreateNbt().putString("CarrierMode", next.name());
        player.sendMessage(Text.translatable("tooltip.icbm_port.carrier_mode", next.name()), true);
      }
      return TypedActionResult.success(stack, world.isClient);
    }
    ItemStack stored = CarrierData.cargo(stack);
    ItemStack other =
        player.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
    ItemStack cargo = stored.isEmpty() ? other.copy() : stored;
    if (!CarrierData.allowed(cargo)) return TypedActionResult.fail(stack);
    cargo.setCount(1);
    if (!world.isClient) {
      CargoEntity carrier = carrier(world, player, stack);
      carrier.setCargo(cargo);
      if (!world.spawnEntity(carrier)) return TypedActionResult.fail(stack);
      carrier.deployCargo();
      if (!player.getAbilities().creativeMode) {
        if (stored.isEmpty()) other.decrement(1);
        stack.decrement(1);
      }
    }
    return TypedActionResult.success(stack, world.isClient);
  }

  @Override
  public void appendTooltip(
      ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
    ItemStack cargo = CarrierData.cargo(stack);
    tooltip.add(
        Text.translatable("tooltip.icbm_port.carrier_mode", CarrierData.mode(stack).name()));
    if (!cargo.isEmpty())
      tooltip.add(Text.translatable("tooltip.icbm_port.held_contents", cargo.getName()));
  }
}
