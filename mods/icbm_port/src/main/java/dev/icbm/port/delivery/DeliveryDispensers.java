package dev.icbm.port.delivery;

import net.minecraft.block.*;
import net.minecraft.block.dispenser.*;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Adapted rail-height and projectile behavior from the upstream dispenser hooks. */
final class DeliveryDispensers {
  static void register() {
    DeliveryContent.GRENADES.forEach(
        (payload, item) ->
            DispenserBlock.registerBehavior(
                item,
                new ProjectileDispenserBehavior() {
                  @Override
                  protected ProjectileEntity createProjectile(
                      World world, Position position, ItemStack stack) {
                    GrenadeEntity grenade = new GrenadeEntity(DeliveryContent.GRENADE, world);
                    grenade.setPayload(payload);
                    grenade.setItem(stack);
                    grenade.setPosition(position.getX(), position.getY(), position.getZ());
                    return grenade;
                  }

                  @Override
                  protected float getForce() {
                    return .5F;
                  }

                  @Override
                  protected float getVariation() {
                    return 1;
                  }
                }));
    DeliveryContent.BOMB_CARTS.forEach(
        (payload, item) ->
            DispenserBlock.registerBehavior(
                item,
                new ItemDispenserBehavior() {
                  @Override
                  protected ItemStack dispenseSilently(BlockPointer pointer, ItemStack stack) {
                    Direction facing = pointer.getBlockState().get(DispenserBlock.FACING);
                    var world = pointer.getWorld();
                    BlockPos pos = pointer.getPos().offset(facing);
                    var state = world.getBlockState(pos);
                    double offset;
                    if (state.isIn(BlockTags.RAILS)) offset = ascending(state) ? .6 : .1;
                    else {
                      var below = world.getBlockState(pos.down());
                      if (!state.isAir() || !below.isIn(BlockTags.RAILS))
                        return super.dispenseSilently(pointer, stack);
                      offset = facing != Direction.DOWN && ascending(below) ? -.4 : -.9;
                    }
                    BombCartEntity cart = new BombCartEntity(DeliveryContent.BOMB_CART, world);
                    cart.setPayload(payload);
                    cart.setPosition(
                        pointer.getX() + facing.getOffsetX() * 1.125,
                        Math.floor(pointer.getY()) + facing.getOffsetY() + offset,
                        pointer.getZ() + facing.getOffsetZ() * 1.125);
                    if (stack.hasCustomName()) cart.setCustomName(stack.getName());
                    if (world.spawnEntity(cart)) stack.decrement(1);
                    return stack;
                  }
                }));
  }

  private static boolean ascending(BlockState state) {
    return state.getBlock() instanceof AbstractRailBlock rail
        && state.get(rail.getShapeProperty()).isAscending();
  }

  private DeliveryDispensers() {}
}
