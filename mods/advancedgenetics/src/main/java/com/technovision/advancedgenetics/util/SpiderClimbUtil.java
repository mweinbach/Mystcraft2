package com.technovision.advancedgenetics.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class SpiderClimbUtil {

    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public static boolean canStartClimb(Player player, BlockPos blockPos) {
        Level world = player.getLevel();
        for (Direction side : SIDES) {
            BlockPos pos = blockPos.relative(side);
            if (isClimbable(world.getBlockState(pos)) && isClimbable(world.getBlockState(pos.above()))) {
                return true;
            }
        }
        return false;
    }

    public static boolean canContinueClimb(Player player, BlockPos blockPos) {
        Level world = player.getLevel();
        for (Direction side : SIDES) {
            if (isClimbable(world.getBlockState(blockPos.relative(side)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClimbable(BlockState state) {
        return state.getBlock() != Blocks.AIR && !state.is(BlockTags.REPLACEABLE_PLANTS);
    }
}
