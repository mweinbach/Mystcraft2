/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.extensions.common.IClientBlockExtensions;
import ports.mekanism.lib.common.ToolAction;
import ports.mekanism.lib.common.ToolActions;

/**
 * Methods Forge adds to {@link Block}, injected into it on Fabric. The port's mixins call them where Forge's patches
 * do.
 */
public interface IForgeBlock {

    private Block self() {
        return (Block) this;
    }

    default int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getLightEmission();
    }

    default boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        self().playerWillDestroy(level, pos, state, player);
        return level.setBlock(pos, fluid.createLegacyBlock(), level.isClientSide ? Block.UPDATE_ALL_IMMEDIATE : Block.UPDATE_ALL);
    }

    default float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return self().getExplosionResistance();
    }

    default ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return self().getCloneItemStack(level, pos, state);
    }

    default boolean isPortalFrame(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.OBSIDIAN);
    }

    default int getExpDrop(BlockState state, LevelReader level, RandomSource random, BlockPos pos, int fortuneLevel, int silkTouchLevel) {
        return 0;
    }

    default BlockState rotate(BlockState state, LevelAccessor level, BlockPos pos, Rotation direction) {
        return state.rotate(direction);
    }

    default void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
    }

    default boolean shouldCheckWeakPower(BlockState state, LevelReader level, BlockPos pos, Direction side) {
        return state.isRedstoneConductor(level, pos);
    }

    default boolean getWeakChanges(BlockState state, LevelReader level, BlockPos pos) {
        return false;
    }

    default SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return self().getSoundType(state);
    }

    default int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return ((FireBlock) Blocks.FIRE).getBurnOdds(state);
    }

    default boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getFlammability(level, pos, direction) > 0;
    }

    default void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction, @Nullable LivingEntity igniter) {
    }

    default int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return ((FireBlock) Blocks.FIRE).getIgniteOdds(state);
    }

    default boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return self().dropFromExplosion(explosion);
    }

    default void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        self().wasExploded(level, pos, explosion);
    }

    default boolean shouldDisplayFluidOverlay(BlockState state, BlockAndTintGetter level, BlockPos pos, FluidState fluidState) {
        return false;
    }

    @Nullable
    default BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction toolAction, boolean simulate) {
        if (!context.getItemInHand().canPerformAction(toolAction)) {
            return null;
        } else if (toolAction == ToolActions.AXE_STRIP) {
            Block stripped = AxeItem.STRIPPABLES.get(state.getBlock());
            return stripped == null ? null : stripped.withPropertiesOf(state);
        } else if (toolAction == ToolActions.AXE_SCRAPE) {
            return WeatheringCopper.getPrevious(state).orElse(null);
        } else if (toolAction == ToolActions.AXE_WAX_OFF) {
            Block unwaxed = HoneycombItem.WAX_OFF_BY_BLOCK.get().get(state.getBlock());
            return unwaxed == null ? null : unwaxed.withPropertiesOf(state);
        } else if (toolAction == ToolActions.SHOVEL_FLATTEN) {
            return ShovelItem.FLATTENABLES.get(state.getBlock());
        } else if (toolAction == ToolActions.HOE_TILL) {
            return tilledState(state, context);
        }
        return null;
    }

    @Nullable
    private static BlockState tilledState(BlockState state, UseOnContext context) {
        Block block = state.getBlock();
        if (block == Blocks.ROOTED_DIRT) {
            return Blocks.DIRT.defaultBlockState();
        } else if (context.getClickedFace() == Direction.DOWN || !context.getLevel().getBlockState(context.getClickedPos().above()).isAir()) {
            return null;
        } else if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT_PATH || block == Blocks.DIRT) {
            return Blocks.FARMLAND.defaultBlockState();
        } else if (block == Blocks.COARSE_DIRT) {
            return Blocks.DIRT.defaultBlockState();
        }
        return null;
    }

    default boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return state.isSignalSource() && direction != null;
    }

    /**
     * Supplies the block's client behavior; read once through {@link IClientBlockExtensions#of}.
     */
    default void initializeClient(Consumer<IClientBlockExtensions> consumer) {
    }
}
