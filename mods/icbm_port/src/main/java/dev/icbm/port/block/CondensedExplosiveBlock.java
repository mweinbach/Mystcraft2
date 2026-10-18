package dev.icbm.port.block;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.PrimedExplosiveEntity;
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

public final class CondensedExplosiveBlock extends Block {
    public CondensedExplosiveBlock(Settings settings) { super(settings); }

    public boolean prime(World world, BlockPos pos, int fuse) {
        if (world.isClient) return false;
        PrimedExplosiveEntity entity = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, world);
        entity.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.setFuse(fuse);
        if (!world.spawnEntity(entity)) return false;
        world.playSound(null, pos, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.BLOCKS, 1, 1);
        return true;
    }

    private void primeAndRemove(World world, BlockPos pos) {
        if (prime(world, pos, IcbmPort.FUSE_TICKS)) world.removeBlock(pos, false);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.isOf(this) && world.isReceivingRedstonePower(pos)) primeAndRemove(world, pos);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block source, BlockPos sourcePos, boolean notify) {
        if (world.isReceivingRedstonePower(pos)) primeAndRemove(world, pos);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(Items.FLINT_AND_STEEL) && !stack.isOf(Items.FIRE_CHARGE)) return ActionResult.PASS;
        if (!world.isClient && prime(world, pos, IcbmPort.FUSE_TICKS)) {
            world.removeBlock(pos, false);
            if (!player.getAbilities().creativeMode) {
                if (stack.isOf(Items.FLINT_AND_STEEL)) stack.damage(1, player, p -> p.sendToolBreakStatus(hand));
                else stack.decrement(1);
            }
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void onProjectileHit(World world, BlockState state, BlockHitResult hit, ProjectileEntity projectile) {
        Entity owner = projectile.getOwner();
        if (!world.isClient && projectile.isOnFire() && (owner == null || owner instanceof PlayerEntity)
                && projectile.canModifyAt(world, hit.getBlockPos())) primeAndRemove(world, hit.getBlockPos());
    }

    @Override public boolean shouldDropItemsOnExplosion(Explosion explosion) { return false; }
    @Override public void onDestroyedByExplosion(World world, BlockPos pos, Explosion explosion) {
        prime(world, pos, 10 + world.random.nextInt(20));
    }
}
