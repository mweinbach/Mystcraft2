package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Bonemeal grows flowers and berries; using a ripe shrub harvests its fruit. */
final class WestIndianLilacBlock extends AncientTallPlantBlock implements BonemealableBlock {
    private static final IntegerProperty AGE = BlockStateProperties.AGE_2;
    WestIndianLilacBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(AGE, 0));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AGE);
    }
    @Override
    public boolean isValidBonemealTarget(BlockGetter level, BlockPos pos, BlockState state, boolean clientSide) {
        return state.getValue(AGE) < 2;
    }
    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) { return true; }
    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos bottom = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        BlockState lower = level.getBlockState(bottom);
        if (lower.is(this) && lower.getValue(AGE) < 2) setAge(level, bottom, lower, lower.getValue(AGE) + 1);
    }
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockPos bottom = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        BlockState lower = level.getBlockState(bottom);
        if (!lower.is(this) || lower.getValue(AGE) < 2) return InteractionResult.PASS;
        if (!level.isClientSide) {
            setAge(level, bottom, lower, 1);
            Block.popResource(level, bottom, new ItemStack(PlantRegistry.LILAC_BERRIES.get()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    private static void setAge(Level level, BlockPos bottom, BlockState lower, int age) {
        level.setBlock(bottom, lower.setValue(AGE, age), Block.UPDATE_CLIENTS);
        BlockState top = level.getBlockState(bottom.above());
        if (top.is(lower.getBlock())) level.setBlock(bottom.above(), top.setValue(AGE, age), Block.UPDATE_CLIENTS);
    }
}
