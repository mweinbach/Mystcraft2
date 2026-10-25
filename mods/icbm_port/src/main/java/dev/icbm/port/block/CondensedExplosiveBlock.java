package dev.icbm.port.block;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import dev.icbm.port.payload.PayloadType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;

public class CondensedExplosiveBlock extends Block {
  private final PayloadType payload;

  public CondensedExplosiveBlock(Settings settings) {
    this(settings, PayloadType.CONDENSED);
  }

  public CondensedExplosiveBlock(Settings settings, PayloadType payload) {
    super(settings);
    this.payload = payload;
  }

  public PayloadType getPayload() {
    return payload;
  }

  public boolean prime(World world, BlockPos pos, int fuse) {
    return prime(world, pos, fuse, null);
  }

  /** Copies data held by the placed block (still present at {@code pos}) onto the primed entity. */
  protected void configurePrimed(World world, BlockPos pos, PrimedExplosiveEntity entity) {}

  public boolean prime(World world, BlockPos pos, int fuse, Entity owner) {
    if (world.isClient) return false;
    PrimedExplosiveEntity entity = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, world);
    entity.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    entity.setFuse(fuse);
    entity.setPayload(payload);
    entity.setOwner(owner);
    configurePrimed(world, pos, entity);
    if (!world.spawnEntity(entity)) return false;
    world.playSound(null, pos, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1, 1);
    return true;
  }

  private void primeAndRemove(World world, BlockPos pos) {
    if (prime(world, pos, payload.blockFuseTicks())) world.removeBlock(pos, false);
  }

  @Override
  public void onBlockAdded(
      BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
    if (!oldState.isOf(this) && world.isReceivingRedstonePower(pos)) primeAndRemove(world, pos);
  }

  @Override
  public void neighborUpdate(
      BlockState state,
      World world,
      BlockPos pos,
      Block source,
      BlockPos sourcePos,
      boolean notify) {
    if (world.isReceivingRedstonePower(pos)) primeAndRemove(world, pos);
  }

  @Override
  public ActionResult onUse(
      BlockState state,
      World world,
      BlockPos pos,
      PlayerEntity player,
      Hand hand,
      BlockHitResult hit) {
    if (player.isSpectator()) return ActionResult.PASS;
    ItemStack stack = player.getStackInHand(hand);
    if (!stack.isOf(Items.FLINT_AND_STEEL) && !stack.isOf(Items.FIRE_CHARGE))
      return ActionResult.PASS;
    if (!world.isClient && prime(world, pos, payload.blockFuseTicks(), player)) {
      world.removeBlock(pos, false);
      if (!player.getAbilities().creativeMode) {
        if (stack.isOf(Items.FLINT_AND_STEEL))
          stack.damage(1, player, p -> p.sendToolBreakStatus(hand));
        else stack.decrement(1);
      }
    }
    return ActionResult.success(world.isClient);
  }

  @Override
  public void onProjectileHit(
      World world, BlockState state, BlockHitResult hit, ProjectileEntity projectile) {
    Entity owner = projectile.getOwner();
    if (!world.isClient
        && projectile.isOnFire()
        && (owner == null || owner instanceof PlayerEntity)
        && projectile.canModifyAt(world, hit.getBlockPos()))
      if (prime(world, hit.getBlockPos(), payload.blockFuseTicks(), owner))
        world.removeBlock(hit.getBlockPos(), false);
  }

  @Override
  public boolean shouldDropItemsOnExplosion(Explosion explosion) {
    return false;
  }

  @Override
  public void onDestroyedByExplosion(World world, BlockPos pos, Explosion explosion) {
    prime(world, pos, 10 + world.random.nextInt(20), explosion.getCausingEntity());
  }
}
