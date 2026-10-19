package net.mystcraft.minecraft.fissure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.StarFissureRegistry;

/** The end-portal projection renderer needs only the fissure's block position. */
public final class StarFissureBlockEntity extends BlockEntity {
    public StarFissureBlockEntity(BlockPos pos, BlockState state) {
        super(StarFissureRegistry.STAR_FISSURE_ENTITY.get(), pos, state);
    }
}
