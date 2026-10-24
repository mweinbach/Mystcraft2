/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.common.IPlantable;
import ports.projecte.lib.common.ToolAction;
import ports.projecte.fabric.mixin.BushBlockAccessor;
import net.minecraft.world.level.block.BushBlock;

/**
 * Methods Forge adds to {@link BlockState}, injected into it on Fabric. Each one asks the state's block.
 */
public interface IForgeBlockState {

    private BlockState self() {
        return (BlockState) this;
    }

    /**
     * Whether this block can hold the plant on its {@code facing} side. A bush asks its own placement rule; other
     * plants (cactus, sugar cane) ask whether they would survive there.
     */
    default boolean canSustainPlant(BlockGetter level, BlockPos pos, Direction facing, IPlantable plantable) {
        BlockState self = (BlockState) this;
        BlockState plant = plantable.projecte$getPlant(level, pos.relative(facing));
        if (plant.getBlock() instanceof BushBlock bush) {
            return ((BushBlockAccessor) bush).projecte$mayPlaceOn(self, level, pos);
        }
        return level instanceof LevelReader reader && plant.canSurvive(reader, pos.relative(facing));
    }

    default int projecte$getLightEmission(BlockGetter level, BlockPos pos) {
        return self().getBlock().projecte$getLightEmission(self(), level, pos);
    }

    default boolean projecte$onDestroyedByPlayer(Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return self().getBlock().projecte$onDestroyedByPlayer(self(), level, pos, player, willHarvest, fluid);
    }

    default float projecte$getExplosionResistance(BlockGetter level, BlockPos pos, Explosion explosion) {
        return self().getBlock().projecte$getExplosionResistance(self(), level, pos, explosion);
    }

    default ItemStack projecte$getCloneItemStack(HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return self().getBlock().projecte$getCloneItemStack(self(), target, level, pos, player);
    }

    default boolean projecte$isPortalFrame(BlockGetter level, BlockPos pos) {
        return self().getBlock().projecte$isPortalFrame(self(), level, pos);
    }

    default int projecte$getExpDrop(LevelReader level, RandomSource random, BlockPos pos, int fortuneLevel, int silkTouchLevel) {
        return self().getBlock().projecte$getExpDrop(self(), level, random, pos, fortuneLevel, silkTouchLevel);
    }

    default BlockState projecte$rotate(LevelAccessor level, BlockPos pos, Rotation direction) {
        return self().getBlock().projecte$rotate(self(), level, pos, direction);
    }

    default void projecte$onNeighborChange(LevelReader level, BlockPos pos, BlockPos neighbor) {
        self().getBlock().projecte$onNeighborChange(self(), level, pos, neighbor);
    }

    default boolean projecte$shouldCheckWeakPower(LevelReader level, BlockPos pos, Direction side) {
        return self().getBlock().projecte$shouldCheckWeakPower(self(), level, pos, side);
    }

    default boolean projecte$getWeakChanges(LevelReader level, BlockPos pos) {
        return self().getBlock().projecte$getWeakChanges(self(), level, pos);
    }

    default SoundType projecte$getSoundType(LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return self().getBlock().projecte$getSoundType(self(), level, pos, entity);
    }

    default int projecte$getFlammability(BlockGetter level, BlockPos pos, Direction face) {
        return self().getBlock().projecte$getFlammability(self(), level, pos, face);
    }

    default boolean projecte$isFlammable(BlockGetter level, BlockPos pos, Direction face) {
        return self().getBlock().projecte$isFlammable(self(), level, pos, face);
    }

    default void projecte$onCaughtFire(Level level, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
        self().getBlock().projecte$onCaughtFire(self(), level, pos, face, igniter);
    }

    default float projecte$getFriction(LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return self().getBlock().projecte$getFriction(self(), level, pos, entity);
    }

    @Nullable
    default float[] projecte$getBeaconColorMultiplier(LevelReader level, BlockPos pos, BlockPos beaconPos) {
        return self().getBlock().projecte$getBeaconColorMultiplier(self(), level, pos, beaconPos);
    }

    default int projecte$getFireSpreadSpeed(BlockGetter level, BlockPos pos, Direction face) {
        return self().getBlock().projecte$getFireSpreadSpeed(self(), level, pos, face);
    }

    default boolean projecte$canDropFromExplosion(BlockGetter level, BlockPos pos, Explosion explosion) {
        return self().getBlock().projecte$canDropFromExplosion(self(), level, pos, explosion);
    }

    default void projecte$onBlockExploded(Level level, BlockPos pos, Explosion explosion) {
        self().getBlock().projecte$onBlockExploded(self(), level, pos, explosion);
    }

    default boolean projecte$shouldDisplayFluidOverlay(BlockAndTintGetter level, BlockPos pos, FluidState fluidState) {
        return self().getBlock().projecte$shouldDisplayFluidOverlay(self(), level, pos, fluidState);
    }

    @Nullable
    default BlockState getToolModifiedState(UseOnContext context, ToolAction toolAction, boolean simulate) {
        return self().getBlock().getToolModifiedState(self(), context, toolAction, simulate);
    }

    default boolean projecte$canConnectRedstone(BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return self().getBlock().projecte$canConnectRedstone(self(), level, pos, direction);
    }

    /**
     * The name Forge's redstone-wire patch calls. Dust asks the neighbor through this and the block answers
     * {@link #canConnectRedstone}.
     */
    default boolean projecte$canRedstoneConnectTo(BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return projecte$canConnectRedstone(level, pos, direction);
    }
}
