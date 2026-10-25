package com.agricraft.agricraft.common.block.irrigation;

import com.agricraft.agricraft.common.block.entity.irrigation.SprinklerBlockEntity;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import com.agricraft.agricraft.common.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sprinkler, ported from AgriCraft 3's {@code BlockSprinkler} (MIT). It hangs below an irrigation channel and pops off
 * when the channel is removed.
 */
public class SprinklerBlock extends Block implements EntityBlock {

	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	private static final VoxelShape SHAPE = Shapes.join(
			Block.box(5, 15, 5, 11, 16, 11),
			Block.box(8 - 0.74 * Math.sqrt(2), 4, 8 - 0.74 * Math.sqrt(2), 8 + 0.74 * Math.sqrt(2), 15, 8 + 0.74 * Math.sqrt(2)),
			BooleanOp.OR);

	public SprinklerBlock() {
		super(Properties.of(Material.WOOD).strength(1.0F, 3.0F).sound(SoundType.WOOD).noOcclusion());
		this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SprinklerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ModBlockEntityTypes.SPRINKLER.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<SprinklerBlockEntity>) SprinklerBlockEntity::ticker : null;
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.canSurvive(this.defaultBlockState(), context.getLevel(), context.getClickedPos()) ? this.defaultBlockState() : null;
	}

	@Override
	@SuppressWarnings("deprecation")
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.above()).getBlock() instanceof IrrigationChannelBlock;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.UP && !this.canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return state;
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootContext.Builder params) {
		List<ItemStack> drops = super.getDrops(state, params);
		drops.add(new ItemStack(ModItems.SPRINKLER.get()));
		return drops;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

}
