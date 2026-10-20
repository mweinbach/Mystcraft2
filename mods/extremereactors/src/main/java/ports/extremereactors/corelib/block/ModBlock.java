/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import ports.extremereactors.platform.Platform;

/**
 * Base block. Subclasses describe their block state through {@link #buildBlockState} and
 * {@link #buildDefaultState}, and forward neighbor changes, removal and GUI requests to their
 * {@link AbstractModBlockEntity}.
 */
public class ModBlock extends Block {

    public ModBlock(final Block.Properties properties) {

        super(properties);
        this.registerDefaultState(this.buildDefaultState(this.getStateDefinition().any()));
    }

    public BlockItem createBlockItem(final Item.Properties properties) {
        return new BlockItem(this, properties);
    }

    //region block state

    /**
     * Add the properties of this block state. Called from the {@link Block} constructor: do not use instance fields.
     */
    protected void buildBlockState(final StateDefinition.Builder<Block, BlockState> builder) {
    }

    protected BlockState buildDefaultState(final BlockState state) {
        return state;
    }

    @Override
    protected final void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        this.buildBlockState(builder);
    }

    //endregion
    //region Block

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(final BlockState state, final Level world, final BlockPos position,
                                 final Player player, final InteractionHand hand, final BlockHitResult hit) {

        final BlockEntity te = world.getBlockEntity(position);

        if (te instanceof AbstractModBlockEntity && te instanceof MenuProvider &&
                ((AbstractModBlockEntity) te).canOpenGui(world, position, state)) {

            if (player instanceof ServerPlayer) {
                Platform.backend().openMenu((ServerPlayer) player, (MenuProvider) te, buffer -> buffer.writeBlockPos(position));
            }

            return InteractionResult.sidedSuccess(world.isClientSide());
        }

        return super.use(state, world, position, player, hand, hit);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(final BlockState state, final Level world, final BlockPos position,
                                final Block neighborBlock, final BlockPos neighborPosition, final boolean isMoving) {

        super.neighborChanged(state, world, position, neighborBlock, neighborPosition, isMoving);

        if (world.getBlockEntity(position) instanceof INeighborChangeListener listener) {
            listener.onNeighborBlockChanged(state, neighborPosition, isMoving);
        }
    }

    /**
     * Forge calls this when an adjacent block entity changes. On Fabric the corelib calls it for the adjacent block
     * entities of {@link INeighborChangeListener.Notifier} blocks.
     */
    public void onNeighborChange(final BlockState state, final LevelReader world, final BlockPos position,
                                 final BlockPos neighborPosition) {

        if (world.getBlockEntity(position) instanceof INeighborChangeListener listener) {
            listener.onNeighborTileChanged(state, neighborPosition);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(final BlockState state, final Level world, final BlockPos position, final BlockState oldState,
                        final boolean isMoving) {

        super.onPlace(state, world, position, oldState, isMoving);

        if (this instanceof INeighborChangeListener.Notifier && !oldState.is(this)) {
            notifyNeighborListeners(world, position);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(final BlockState state, final Level world, final BlockPos position, final BlockState newState,
                         final boolean isMoving) {

        if (!state.is(newState.getBlock())) {

            if (world.getBlockEntity(position) instanceof AbstractModBlockEntity te) {
                te.onBlockReplaced(state, world, position, newState, isMoving);
            }

            super.onRemove(state, world, position, newState, isMoving);

            if (this instanceof INeighborChangeListener.Notifier) {
                notifyNeighborListeners(world, position);
            }

        } else {

            super.onRemove(state, world, position, newState, isMoving);
        }
    }

    //endregion
    //region internals

    /**
     * Tell the {@link INeighborChangeListener} block entities around a position that the block entity there changed.
     */
    static void notifyNeighborListeners(final BlockGetter world, final BlockPos position) {

        for (final Direction direction : Direction.values()) {

            final BlockPos neighborPosition = position.relative(direction);

            if (world instanceof LevelReader && !((LevelReader) world).hasChunkAt(neighborPosition)) {
                continue;
            }

            if (world.getBlockEntity(neighborPosition) instanceof INeighborChangeListener listener) {
                listener.onNeighborTileChanged(world.getBlockState(neighborPosition), position);
            }
        }
    }

    //endregion
}
