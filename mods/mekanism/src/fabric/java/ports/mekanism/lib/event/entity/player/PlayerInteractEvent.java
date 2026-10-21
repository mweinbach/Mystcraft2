/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.eventbus.api.Cancelable;

public class PlayerInteractEvent extends PlayerEvent {

    private final InteractionHand hand;
    private final BlockPos pos;
    @Nullable
    private final Direction face;
    private InteractionResult cancellationResult = InteractionResult.PASS;

    protected PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos, @Nullable Direction face) {
        super(player);
        this.hand = hand;
        this.pos = pos;
        this.face = face;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public ItemStack getItemStack() {
        return getEntity().getItemInHand(hand);
    }

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public Direction getFace() {
        return face;
    }

    public Level getLevel() {
        return getEntity().getLevel();
    }

    public InteractionResult getCancellationResult() {
        return cancellationResult;
    }

    public void setCancellationResult(InteractionResult result) {
        this.cancellationResult = result;
    }

    /**
     * Fired before a player right-clicks a block. {@link #getUseBlock()} and {@link #getUseItem()} decide whether
     * the block's and the item's use run; {@code DEFAULT} keeps vanilla's choice.
     */
    @Cancelable
    public static class RightClickBlock extends PlayerInteractEvent {

        private final BlockHitResult hitVec;
        private Result useBlock = Result.DEFAULT;
        private Result useItem = Result.DEFAULT;

        public RightClickBlock(Player player, InteractionHand hand, BlockPos pos, BlockHitResult hitVec) {
            super(player, hand, pos, hitVec.getDirection());
            this.hitVec = hitVec;
        }

        public BlockHitResult getHitVec() {
            return hitVec;
        }

        public Result getUseBlock() {
            return useBlock;
        }

        public Result getUseItem() {
            return useItem;
        }

        public void setUseBlock(Result triggerBlock) {
            this.useBlock = triggerBlock;
        }

        public void setUseItem(Result triggerItem) {
            this.useItem = triggerItem;
        }
    }
}
