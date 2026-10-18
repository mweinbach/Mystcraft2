package dev.icbm.port.energy;

import dev.icbm.port.IcbmPort;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import team.reborn.energy.api.base.SimpleEnergyItem;

/** Upstream battery capacity and limits, with Fabric transactional item energy support. */
public final class BatteryItem extends Item implements SimpleEnergyItem {
  public BatteryItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(1));
  }

  @Override
  public long getEnergyCapacity(ItemStack stack) {
    return EnergyConfig.get().batteryCapacity();
  }

  @Override
  public long getEnergyMaxInput(ItemStack stack) {
    return EnergyConfig.get().batteryInput();
  }

  @Override
  public long getEnergyMaxOutput(ItemStack stack) {
    return EnergyConfig.get().batteryOutput();
  }

  @Override
  public long getStoredEnergy(ItemStack stack) {
    return MathHelper.clamp(
        SimpleEnergyItem.getStoredEnergyUnchecked(stack), 0, getEnergyCapacity(stack));
  }

  @Override
  public void setStoredEnergy(ItemStack stack, long amount) {
    SimpleEnergyItem.setStoredEnergyUnchecked(
        stack, MathHelper.clamp(amount, 0, getEnergyCapacity(stack)));
  }

  @Override
  public net.minecraft.util.ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
    var player = context.getPlayer();
    if (player == null) return net.minecraft.util.ActionResult.PASS;
    if (context.getWorld().isClient) return net.minecraft.util.ActionResult.SUCCESS;
    var machine =
        team.reborn.energy.api.EnergyStorage.SIDED.find(
            context.getWorld(), context.getBlockPos(), context.getSide());
    if (machine == null) return net.minecraft.util.ActionResult.PASS;
    var held =
        net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext.ofPlayerHand(
                player, context.getHand())
            .find(team.reborn.energy.api.EnergyStorage.ITEM);
    long moved =
        player.isSneaking()
            ? team.reborn.energy.api.EnergyStorageUtil.move(
                machine, held, getEnergyMaxInput(context.getStack()), null)
            : team.reborn.energy.api.EnergyStorageUtil.move(
                held, machine, getEnergyMaxOutput(context.getStack()), null);
    if (moved > 0) player.sendMessage(Text.literal("Transferred " + moved + " E"), true);
    return moved > 0
        ? net.minecraft.util.ActionResult.CONSUME
        : net.minecraft.util.ActionResult.PASS;
  }

  @Override
  public boolean isItemBarVisible(ItemStack stack) {
    return true;
  }

  @Override
  public int getItemBarStep(ItemStack stack) {
    return (int) (13 * getStoredEnergy(stack) / getEnergyCapacity(stack));
  }

  @Override
  public int getItemBarColor(ItemStack stack) {
    return 0x40d060;
  }

  @Override
  public void appendTooltip(
      ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
    tooltip.add(Text.literal(getStoredEnergy(stack) + " / " + getEnergyCapacity(stack) + " E"));
    tooltip.add(
        Text.literal(
            "Transfer: "
                + getEnergyMaxInput(stack)
                + " E in / "
                + getEnergyMaxOutput(stack)
                + " E out"));
  }
}
