package dev.icbm.port.block;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import dev.icbm.port.payload.PayloadType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
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
import org.jetbrains.annotations.Nullable;

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

  /**
   * Copies data held by the placed block onto the primed entity. Explosions clear the block before
   * {@link #onDestroyedByExplosion}, so this receives the state and block entity captured when the
   * block was replaced rather than reading the (now empty) position.
   */
  protected void configurePrimed(
      BlockState state, @Nullable BlockEntity blockEntity, PrimedExplosiveEntity entity) {}

  public boolean prime(World world, BlockPos pos, int fuse, Entity owner) {
    return prime(world, pos, fuse, owner, false);
  }

  /**
   * Adapted from MIT ICBM-Classic {@code TileEntityExplosive#trigger}: spawns the primed entity
   * with its upstream pop, optionally burning (fire-triggered), and removes nothing itself.
   */
  public boolean prime(World world, BlockPos pos, int fuse, Entity owner, boolean burning) {
    return prime(
        world, pos, fuse, owner, burning, world.getBlockState(pos), world.getBlockEntity(pos));
  }

  private boolean prime(
      World world,
      BlockPos pos,
      int fuse,
      Entity owner,
      boolean burning,
      BlockState state,
      @Nullable BlockEntity blockEntity) {
    if (world.isClient) return false;
    PrimedExplosiveEntity entity = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, world);
    entity.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    entity.applyPrimePop();
    entity.setFuse(fuse);
    entity.setPayload(payload);
    entity.setOwner(owner);
    configurePrimed(state, blockEntity, entity);
    if (burning) entity.setOnFireFor(100);
    if (!world.spawnEntity(entity)) return false;
    world.playSound(null, pos, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1, 1);
    return true;
  }

  private void primeAndRemove(World world, BlockPos pos) {
    if (prime(world, pos, payload.blockFuseTicks())) world.removeBlock(pos, false);
  }

  /** Upstream {@code onBlockPlacedBy}: horizontal fire or lava next to a placed charge ignites it. */
  public static boolean besideFire(World world, BlockPos pos) {
    for (net.minecraft.util.math.Direction side :
        net.minecraft.util.math.Direction.Type.HORIZONTAL) {
      BlockState neighbour = world.getBlockState(pos.offset(side));
      if (neighbour.isIn(net.minecraft.tag.BlockTags.FIRE)
          || neighbour.getFluidState().isIn(net.minecraft.tag.FluidTags.LAVA)) return true;
    }
    return false;
  }

  @Override
  public void onPlaced(
      World world,
      BlockPos pos,
      BlockState state,
      net.minecraft.entity.LivingEntity placer,
      ItemStack stack) {
    super.onPlaced(world, pos, state, placer, stack);
    if (!world.isClient
        && world.getBlockState(pos).isOf(this)
        && besideFire(world, pos)
        && prime(world, pos, payload.blockFuseTicks(), placer, true)) world.removeBlock(pos, false);
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

  /**
   * Upstream {@code BlockExplosive.onBlockExploded} triggers the charge before Forge clears the
   * block. Vanilla 1.19 (and the staged blasts, which follow it) set air first and only then call
   * {@link #onDestroyedByExplosion}, so the replaced state and block entity are kept here for that
   * immediately following call. A single slot suffices: the two calls are back to back on the
   * server thread.
   */
  private static @Nullable Replaced lastReplaced;

  private record Replaced(
      World world, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {}

  @Override
  public void onStateReplaced(
      BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
    if (!world.isClient && !newState.isOf(this))
      lastReplaced = new Replaced(world, pos.toImmutable(), state, world.getBlockEntity(pos));
    super.onStateReplaced(state, world, pos, newState, moved);
  }

  /**
   * Upstream {@code onBlockExploded} triggers the tile with the normal configured block fuse
   * (not vanilla TNT's random 10–30 ticks), so a condensed chain detonates on the next tick.
   */
  @Override
  public void onDestroyedByExplosion(World world, BlockPos pos, Explosion explosion) {
    Replaced replaced = lastReplaced;
    lastReplaced = null;
    int fuse = payload.blockFuseTicks();
    if (replaced != null
        && replaced.world == world
        && replaced.pos.equals(pos)
        && replaced.state.isOf(this))
      prime(
          world, pos, fuse, explosion.getCausingEntity(), false, replaced.state,
          replaced.blockEntity);
    else prime(world, pos, fuse, explosion.getCausingEntity());
  }
}
