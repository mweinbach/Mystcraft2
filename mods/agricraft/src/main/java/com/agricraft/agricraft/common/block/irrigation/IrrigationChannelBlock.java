package com.agricraft.agricraft.common.block.irrigation;

import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationChannelBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationComponentBlockEntity;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import com.agricraft.agricraft.common.registry.ModItems;
import com.agricraft.agricraft.common.registry.ModSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Irrigation channel, ported from AgriCraft 3's {@code BlockIrrigationChannelAbstract}, {@code BlockIrrigationChannelNormal}
 * and {@code BlockIrrigationChannelHollow} (MIT). Normal channels have a hand-operated valve; hollow channels are full
 * blocks whose valve follows redstone.
 */
public class IrrigationChannelBlock extends Block implements EntityBlock {

	public static final BooleanProperty NORTH = BooleanProperty.create("north");
	public static final BooleanProperty EAST = BooleanProperty.create("east");
	public static final BooleanProperty SOUTH = BooleanProperty.create("south");
	public static final BooleanProperty WEST = BooleanProperty.create("west");
	public static final EnumProperty<Valve> VALVE = EnumProperty.create("valve", Valve.class);

	private final boolean hollow;
	private final Map<BlockState, VoxelShape> shapes = new ConcurrentHashMap<>();

	public IrrigationChannelBlock(boolean hollow) {
		super(Properties.of(Material.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion());
		this.hollow = hollow;
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
				.setValue(VALVE, Valve.NONE));
	}

	public static BooleanProperty getConnection(Direction direction) {
		return switch (direction) {
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			default -> NORTH;
		};
	}

	public boolean isHollow() {
		return this.hollow;
	}

	public boolean hasHandWheel() {
		return !this.hollow;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, VALVE);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IrrigationChannelBlockEntity(pos, state);
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ModBlockEntityTypes.IRRIGATION_CHANNEL.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<IrrigationComponentBlockEntity>) IrrigationComponentBlockEntity::ticker : null;
	}

	private static boolean connectsTo(BlockState neighbour) {
		return neighbour.getBlock() instanceof IrrigationChannelBlock || neighbour.getBlock() instanceof IrrigationTankBlock;
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = this.defaultBlockState();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			state = state.setValue(getConnection(dir), connectsTo(context.getLevel().getBlockState(context.getClickedPos().relative(dir))));
		}
		return state;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (!direction.getAxis().isHorizontal()) {
			return state;
		}
		return state.setValue(getConnection(direction), connectsTo(neighborState));
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		Valve valve = state.getValue(VALVE);
		if (this.hollow || !valve.hasValve()) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide) {
			level.setBlock(pos, state.setValue(VALVE, valve.toggleValve()), Block.UPDATE_ALL);
			playValveSound(level, pos);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if (!this.hollow || level.isClientSide) {
			return;
		}
		Valve valve = state.getValue(VALVE);
		if (valve.hasValve()) {
			boolean shouldBeClosed = level.hasNeighborSignal(pos);
			boolean isClosed = !valve.canTransfer();
			if (shouldBeClosed != isClosed) {
				level.setBlock(pos, state.setValue(VALVE, valve.toggleValve()), Block.UPDATE_ALL);
				playValveSound(level, pos);
			}
		}
	}

	public static void playValveSound(Level level, BlockPos pos) {
		level.playSound(null, pos, ModSoundEvents.VALVE.get(), SoundSource.BLOCKS,
				2.5F, 2.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8F);
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootContext.Builder params) {
		List<ItemStack> drops = super.getDrops(state, params);
		drops.add(new ItemStack(this.hollow ? ModItems.IRRIGATION_CHANNEL_HOLLOW.get() : ModItems.IRRIGATION_CHANNEL.get()));
		if (state.getValue(VALVE).hasValve()) {
			drops.add(new ItemStack(ModItems.CHANNEL_VALVE.get()));
		}
		return drops;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shapes.computeIfAbsent(state, this.hollow ? IrrigationChannelBlock::hollowShape : IrrigationChannelBlock::normalShape);
	}

	private static VoxelShape normalShape(BlockState state) {
		return Stream.concat(
				Stream.of(state.getValue(VALVE).hasValve() ? NormalShapes.BASE_VALVE : NormalShapes.BASE),
				Direction.Plane.HORIZONTAL.stream().map(dir -> {
					boolean connected = state.getValue(getConnection(dir));
					return switch (dir) {
						case NORTH -> connected ? NormalShapes.CHANNEL_NORTH : NormalShapes.NONE_NORTH;
						case SOUTH -> connected ? NormalShapes.CHANNEL_SOUTH : NormalShapes.NONE_SOUTH;
						case WEST -> connected ? NormalShapes.CHANNEL_WEST : NormalShapes.NONE_WEST;
						default -> connected ? NormalShapes.CHANNEL_EAST : NormalShapes.NONE_EAST;
					};
				})
		).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get().optimize();
	}

	private static VoxelShape hollowShape(BlockState state) {
		return Stream.concat(
				Stream.of(HollowShapes.BASE),
				Direction.Plane.HORIZONTAL.stream().filter(dir -> !state.getValue(getConnection(dir))).map(dir -> switch (dir) {
					case NORTH -> HollowShapes.NONE_NORTH;
					case SOUTH -> HollowShapes.NONE_SOUTH;
					case WEST -> HollowShapes.NONE_WEST;
					default -> HollowShapes.NONE_EAST;
				})
		).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get().optimize();
	}

	private static VoxelShape union(VoxelShape... shapes) {
		return Stream.of(shapes).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();
	}

	private static final class NormalShapes {
		static final VoxelShape BASE = union(
				Block.box(5, 5, 5, 11, 6, 11),
				Block.box(5, 6, 5, 6, 10, 6),
				Block.box(5, 6, 10, 6, 10, 11),
				Block.box(10, 6, 5, 11, 10, 6),
				Block.box(10, 6, 10, 11, 10, 11));
		static final VoxelShape BASE_VALVE = union(BASE, Block.box(5, 10, 5, 11, 16, 11));
		static final VoxelShape CHANNEL_NORTH = union(
				Block.box(5, 5, 0, 11, 6, 5),
				Block.box(5, 6, 0, 6, 10, 5),
				Block.box(10, 6, 0, 11, 10, 5));
		static final VoxelShape CHANNEL_SOUTH = CHANNEL_NORTH.move(0, 0, 11 / 16.0);
		static final VoxelShape CHANNEL_WEST = union(
				Block.box(0, 5, 5, 5, 6, 11),
				Block.box(0, 6, 5, 5, 10, 6),
				Block.box(0, 6, 10, 5, 10, 11));
		static final VoxelShape CHANNEL_EAST = CHANNEL_WEST.move(11 / 16.0, 0, 0);
		static final VoxelShape NONE_NORTH = Block.box(6, 6, 5, 10, 10, 6);
		static final VoxelShape NONE_SOUTH = NONE_NORTH.move(0, 0, 5 / 16.0);
		static final VoxelShape NONE_WEST = Block.box(5, 6, 6, 6, 10, 10);
		static final VoxelShape NONE_EAST = NONE_WEST.move(5 / 16.0, 0, 0);
	}

	private static final class HollowShapes {
		static final VoxelShape BASE = union(
				Block.box(0, 0, 0, 16, 6, 16),
				Block.box(0, 10, 0, 16, 16, 16),
				Block.box(0, 6, 0, 6, 10, 6),
				Block.box(0, 6, 10, 6, 10, 16),
				Block.box(10, 6, 0, 16, 10, 6),
				Block.box(10, 6, 10, 16, 10, 16));
		static final VoxelShape NONE_NORTH = Block.box(6, 6, 0, 10, 10, 6);
		static final VoxelShape NONE_SOUTH = NONE_NORTH.move(0, 0, 10 / 16.0);
		static final VoxelShape NONE_WEST = Block.box(0, 6, 6, 6, 10, 10);
		static final VoxelShape NONE_EAST = NONE_WEST.move(10 / 16.0, 0, 0);
	}

	public enum Valve implements StringRepresentable {
		NONE(true, false),
		OPEN(true, true),
		CLOSED(false, true);

		private final boolean transfer;
		private final boolean valve;

		Valve(boolean transfer, boolean valve) {
			this.transfer = transfer;
			this.valve = valve;
		}

		public boolean canTransfer() {
			return this.transfer;
		}

		public boolean hasValve() {
			return this.valve;
		}

		public Valve toggleValve() {
			if (this.hasValve()) {
				return this.canTransfer() ? CLOSED : OPEN;
			}
			return NONE;
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

}
