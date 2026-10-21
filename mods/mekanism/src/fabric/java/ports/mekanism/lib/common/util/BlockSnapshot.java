/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A block's state and block entity data at one moment.
 */
public final class BlockSnapshot {

    private final ResourceKey<Level> dim;
    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;
    @Nullable
    private final CompoundTag nbt;

    private BlockSnapshot(ResourceKey<Level> dim, LevelAccessor level, BlockPos pos, BlockState state, @Nullable CompoundTag nbt) {
        this.dim = dim;
        this.level = level;
        this.pos = pos.immutable();
        this.state = state;
        this.nbt = nbt;
    }

    public static BlockSnapshot create(ResourceKey<Level> dim, LevelAccessor level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return new BlockSnapshot(dim, level, pos, level.getBlockState(pos), blockEntity == null ? null : blockEntity.saveWithFullMetadata());
    }

    public ResourceKey<Level> getDimension() {
        return dim;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getReplacedBlock() {
        return state;
    }

    public BlockState getCurrentBlock() {
        return level.getBlockState(pos);
    }

    @Nullable
    public CompoundTag getTag() {
        return nbt;
    }

    /**
     * Puts the saved block back. Unless forced, nothing changes when the block at the position is no longer the one
     * that replaced the snapshot.
     */
    public boolean restore(boolean force, boolean notifyNeighbors) {
        BlockState current = getCurrentBlock();
        if (current.getBlock() != state.getBlock() && !force) {
            return false;
        }
        level.setBlock(pos, state, notifyNeighbors ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
        if (nbt != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                blockEntity.load(nbt);
            }
        }
        return true;
    }
}
