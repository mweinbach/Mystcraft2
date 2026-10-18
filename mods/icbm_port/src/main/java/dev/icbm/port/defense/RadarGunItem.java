package dev.icbm.port.defense;

import dev.icbm.port.IcbmPort;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.world.World;

/** Upstream radar gun stores a traced GPS point, rather than scanning entities. */
public final class RadarGunItem extends Item {
  RadarGunItem() {
    super(new Settings().group(IcbmPort.GROUP).maxCount(1));
  }

  @Override
  public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
    ItemStack stack = player.getStackInHand(hand);
    if (!world.isClient) {
      if (player.isSneaking()) {
        stack.removeSubNbt("IcbmTarget");
        player.sendMessage(Text.literal("Target cleared"), true);
      } else {
        HitResult hit = player.raycast(200, 1, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
          NbtCompound target = stack.getOrCreateSubNbt("IcbmTarget");
          target.putLong("Position", blockHit.getBlockPos().asLong());
          target.putString("Dimension", world.getRegistryKey().getValue().toString());
          player.sendMessage(
              Text.literal("Target: " + blockHit.getBlockPos().toShortString()), true);
        } else player.sendMessage(Text.literal("No block within 200 blocks"), true);
      }
    }
    return TypedActionResult.success(stack, world.isClient);
  }
}
