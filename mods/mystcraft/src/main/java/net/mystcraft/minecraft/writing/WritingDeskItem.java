package net.mystcraft.minecraft.writing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.mystcraft.integration.registry.WritingDeskRegistry;

/** Places both halves atomically; extension uses the same head/foot orientation as its base. */
public final class WritingDeskItem extends Item {
    private final boolean extension;
    public WritingDeskItem(Properties properties, boolean extension) { super(properties); this.extension = extension; }
    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel(); Player player = context.getPlayer();
        if (player == null) return InteractionResult.FAIL;
        BlockPlaceContext placement = new BlockPlaceContext(context);
        BlockPos head;
        Direction facing;
        if (extension) {
            BlockState clicked = level.getBlockState(context.getClickedPos());
            if (!(clicked.getBlock() instanceof WritingDeskBlock) || clicked.getValue(WritingDeskBlock.TOP)) return InteractionResult.PASS;
            BlockPos base = WritingDeskBlock.headPosition(context.getClickedPos(), clicked);
            BlockState baseState = level.getBlockState(base);
            if (!WritingDeskBlock.matches(baseState, clicked, false, false)
                    || !WritingDeskBlock.matches(level.getBlockState(base.relative(clicked.getValue(WritingDeskBlock.FACING))), clicked, true, false)) return InteractionResult.FAIL;
            head = base.above(); facing = clicked.getValue(WritingDeskBlock.FACING);
        } else {
            if (context.getClickedFace() != Direction.UP) return InteractionResult.PASS;
            head = placement.getClickedPos(); facing = player.getDirection().getClockWise();
        }
        BlockPos foot = head.relative(facing);
        BlockState state = WritingDeskRegistry.WRITING_DESK.get().defaultBlockState()
                .setValue(WritingDeskBlock.FACING, facing).setValue(WritingDeskBlock.TOP, extension);
        BlockState footState = state.setValue(WritingDeskBlock.FOOT, true);
        if (!level.getWorldBorder().isWithinBounds(head) || !level.getWorldBorder().isWithinBounds(foot)
                || level.isOutsideBuildHeight(head) || level.isOutsideBuildHeight(foot)
                || !level.getBlockState(head).canBeReplaced(placement) || !level.getBlockState(foot).canBeReplaced(placement)
                || !player.mayUseItemAt(head, context.getClickedFace(), context.getItemInHand())
                || !player.mayUseItemAt(foot, context.getClickedFace(), context.getItemInHand())
                || !level.isUnobstructed(state, head, CollisionContext.of(player))
                || !level.isUnobstructed(footState, foot, CollisionContext.of(player))) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            BlockState previous = level.getBlockState(head);
            if (!level.setBlock(head, state, 2)) return InteractionResult.FAIL;
            if (!level.setBlock(foot, footState, 2)) {
                level.setBlock(head, previous, 3);
                return InteractionResult.FAIL;
            }
            level.updateNeighborsAt(head, state.getBlock()); level.updateNeighborsAt(foot, state.getBlock());
            if (!player.isCreative()) context.getItemInHand().shrink(1);
            level.playSound(null, head, net.minecraft.sounds.SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1, .8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
