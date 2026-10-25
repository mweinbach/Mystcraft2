package dev.icbm.port.launcher;

import dev.icbm.port.radio.RadioMessage;
import dev.icbm.port.radio.RadioNetwork;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Laser detonator. Adapted from MIT ICBM-Classic {@code content/items/ItemLaserDetonator.java}:
 * a 200-block block raycast from the player's eyes; on a hit the item broadcasts a
 * trigger-with-target message carrying the hit point on its channel, so launchers on that channel
 * retarget and fire. A miss reports on the action bar, and a ray that lands on a launcher block is
 * ignored so pointing at your own launcher does nothing. Channel copying is inherited from the
 * remote detonator ({@link RadioItem}).
 */
final class LaserDetonatorItem extends RadioItem {
  /** Upstream {@code ItemLaserDetonator} ray length. */
  public static final int RANGE = 200;

  @Override
  protected boolean transmit(ServerWorld world, PlayerEntity player, int channel) {
    HitResult hit = player.raycast(RANGE, 1.0F, false);
    if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
      player.sendMessage(Text.translatable("message.icbm_port.laser.missed"), true);
      return true;
    }
    var state = world.getBlockState(block.getBlockPos());
    if (state.getBlock() instanceof dev.icbm.port.block.LauncherBlock
        || state.getBlock() instanceof LauncherComponentBlock) return false;
    var aim = block.getPos();
    int heard =
        RadioNetwork.broadcast(
            world,
            new RadioNetwork.PlayerSender(player, channel),
            new RadioMessage.TriggerTarget(channel, aim));
    if (heard == 0)
      player.sendMessage(Text.translatable("message.icbm_port.radio.no_receivers", channel), true);
    return true;
  }
}
