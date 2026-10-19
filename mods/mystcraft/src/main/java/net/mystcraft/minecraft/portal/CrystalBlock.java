package net.mystcraft.minecraft.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;

public final class CrystalBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final DirectionProperty SOURCE = DirectionProperty.create("source");
    public CrystalBlock() {
        super(Properties.of(Material.GLASS).strength(1).sound(SoundType.GLASS).lightLevel(state -> 7).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(SOURCE, Direction.DOWN));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(ACTIVE, SOURCE); }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) { return state.getValue(ACTIVE) ? 15 : 0; }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block changed, BlockPos from, boolean moving) {
        if (!level.isClientSide) PortalController.validate(level, pos);
    }
}
