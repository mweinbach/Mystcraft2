/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.ForgeHooks;
import ports.mekanism.lib.common.util.BlockSnapshot;
import ports.mekanism.lib.eventbus.api.Cancelable;
import ports.mekanism.lib.eventbus.api.Event;

public class BlockEvent extends Event {

    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    public BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    /**
     * Fired before a player breaks a block. Posting it on the Forge bus also asks Fabric's block break listeners, so
     * protection mods can veto Mekanism's machines.
     */
    @Cancelable
    public static class BreakEvent extends BlockEvent {

        private final Player player;
        private int exp;

        public BreakEvent(Level level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
            this.exp = ForgeHooks.getExpDrop(level, pos, state, player);
            ForgeHooks.captureBreakExp(this.exp);
        }

        public Player getPlayer() {
            return player;
        }

        public int getExpToDrop() {
            return isCanceled() ? 0 : exp;
        }

        public void setExpToDrop(int exp) {
            this.exp = exp;
        }
    }

    @Cancelable
    public static class EntityPlaceEvent extends BlockEvent {

        @Nullable
        private final Entity entity;
        private final BlockSnapshot blockSnapshot;
        private final BlockState placedBlock;
        private final BlockState placedAgainst;

        public EntityPlaceEvent(BlockSnapshot blockSnapshot, BlockState placedAgainst, @Nullable Entity entity) {
            super(blockSnapshot.getLevel(), blockSnapshot.getPos(), blockSnapshot.getCurrentBlock());
            this.entity = entity;
            this.blockSnapshot = blockSnapshot;
            this.placedBlock = blockSnapshot.getCurrentBlock();
            this.placedAgainst = placedAgainst;
        }

        @Nullable
        public Entity getEntity() {
            return entity;
        }

        public BlockSnapshot getBlockSnapshot() {
            return blockSnapshot;
        }

        public BlockState getPlacedBlock() {
            return placedBlock;
        }

        public BlockState getPlacedAgainst() {
            return placedAgainst;
        }
    }
}
