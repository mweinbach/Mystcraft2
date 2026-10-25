package dev.icbm.port.launcher;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.radio.RadioMessage;
import dev.icbm.port.radio.RadioNetwork;
import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Remote detonator. Adapted from MIT ICBM-Classic {@code content/items/ItemRadio.java} and {@code
 * ItemRemoteDetonator.java}: using the item on any radio block (launcher, radar, EMP tower, SAM
 * station) copies that block's channel into the item; using it in the air broadcasts a trigger on
 * that channel to every receiver in the dimension, and receivers answer on the player's action bar
 * (upstream {@code FakeRadioSender}). Port differences: channels are integers 1..9999 and there is
 * no item GUI to type a channel, so the channel is always copied from a block.
 */
class RadioItem extends Item {
  /** Item NBT key for the channel (kept from the port's earlier pairing format). */
  public static final String CHANNEL_KEY = "Frequency";

  /** Upstream detonator cooldown in ticks. */
  public static final int COOLDOWN = 20;

  RadioItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(1));
  }

  public static int channel(ItemStack stack) {
    var nbt = stack.getNbt();
    int channel = nbt == null ? 0 : nbt.getInt(CHANNEL_KEY);
    return RadioNetwork.validChannel(channel) ? channel : 0;
  }

  public static void setChannel(ItemStack stack, int channel) {
    stack.getOrCreateNbt().putInt(CHANNEL_KEY, RadioNetwork.validChannel(channel) ? channel : 0);
  }

  /** Upstream {@code ItemRadio#onItemUse}: copy the channel of the radio block that was used. */
  @Override
  public ActionResult useOnBlock(ItemUsageContext context) {
    if (!(context.getWorld().getBlockEntity(context.getBlockPos())
        instanceof RadioNetwork.Receiver radio)) return ActionResult.PASS;
    if (!context.getWorld().isClient && context.getPlayer() != null) {
      int channel = radio.radioChannel();
      setChannel(context.getStack(), channel);
      context
          .getPlayer()
          .sendMessage(
              RadioNetwork.validChannel(channel)
                  ? Text.translatable("message.icbm_port.radio.channel_copied", channel)
                  : Text.translatable("message.icbm_port.radio.channel_unset"),
              true);
    }
    return ActionResult.success(context.getWorld().isClient);
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    if (player.isSpectator()) return TypedActionResult.pass(stack);
    if (world instanceof ServerWorld server) {
      int channel = channel(stack);
      if (channel == 0)
        player.sendMessage(Text.translatable("message.icbm_port.radio.channel_unset"), true);
      else if (transmit(server, player, channel)) player.getItemCooldownManager().set(this, COOLDOWN);
    }
    return TypedActionResult.success(stack, world.isClient);
  }

  /** Upstream {@code ItemRemoteDetonator}: a plain trigger message on the item's channel. */
  protected boolean transmit(ServerWorld world, PlayerEntity player, int channel) {
    int heard =
        RadioNetwork.broadcast(
            world, new RadioNetwork.PlayerSender(player, channel), new RadioMessage.Trigger(channel));
    if (heard == 0)
      player.sendMessage(Text.translatable("message.icbm_port.radio.no_receivers", channel), true);
    return true;
  }

  @Override
  public void appendTooltip(
      ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
    int channel = channel(stack);
    tooltip.add(
        channel == 0
            ? Text.translatable("tooltip.icbm_port.radio.channel_unset")
            : Text.translatable("tooltip.icbm_port.radio.channel", channel));
  }
}
