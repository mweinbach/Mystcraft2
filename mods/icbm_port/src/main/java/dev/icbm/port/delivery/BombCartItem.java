package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.payload.PayloadType;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.ActionResult;

public final class BombCartItem extends Item {
  private final PayloadType payload;

  public BombCartItem(PayloadType payload) {
    super(new Settings().group(IcbmPort.GROUP).maxCount(3));
    this.payload = payload;
  }

  @Override
  public ActionResult useOnBlock(ItemUsageContext context) {
    var world = context.getWorld();
    var pos = context.getBlockPos();
    var state = world.getBlockState(pos);
    if (!state.isIn(BlockTags.RAILS)) return ActionResult.FAIL;
    if (!world.isClient) {
      boolean ascending =
          state.getBlock() instanceof AbstractRailBlock rail
              && state.get(rail.getShapeProperty()).isAscending();
      BombCartEntity cart = new BombCartEntity(DeliveryContent.BOMB_CART, world);
      cart.setPayload(payload);
      cart.setPosition(pos.getX() + .5, pos.getY() + .0625 + (ascending ? .5 : 0), pos.getZ() + .5);
      if (context.getStack().hasCustomName()) cart.setCustomName(context.getStack().getName());
      if (world.spawnEntity(cart)
          && (context.getPlayer() == null || !context.getPlayer().getAbilities().creativeMode))
        context.getStack().decrement(1);
    }
    return ActionResult.success(world.isClient);
  }
}
