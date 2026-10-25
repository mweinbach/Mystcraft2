package com.agricraft.agricraft.common.block.irrigation;

import com.agricraft.agricraft.api.config.IrrigationConfig;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationComponentBlockEntity;
import com.agricraft.agricraft.common.block.entity.irrigation.IrrigationTankBlockEntity;
import com.agricraft.agricraft.common.registry.ModBlockEntityTypes;
import com.agricraft.agricraft.common.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Irrigation tank block, ported from AgriCraft 3's {@code BlockIrrigationTank} (MIT).
 * <p>
 * Differences from AgriCraft 3: InfinityLib's per-wood dynamic textures are not available, so every tank uses oak
 * planks; the invisible "tank water" fluid that let players swim inside a tank is not ported (the water is drawn by
 * the block entity renderer instead). A ladder makes the tank climbable through {@code LivingEntityMixin}.
 */
public class IrrigationTankBlock extends Block implements EntityBlock {

	public static final EnumProperty<Connection> NORTH = EnumProperty.create("north", Connection.class);
	public static final EnumProperty<Connection> EAST = EnumProperty.create("east", Connection.class);
	public static final EnumProperty<Connection> SOUTH = EnumProperty.create("south", Connection.class);
	public static final EnumProperty<Connection> WEST = EnumProperty.create("west", Connection.class);
	public static final BooleanProperty DOWN = BooleanProperty.create("down");
	public static final BooleanProperty LADDER = BooleanProperty.create("ladder");

	public static final VoxelShape SHAPE_DOWN = Block.box(0, 0, 0, 16, 2, 16);
	private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

	public IrrigationTankBlock() {
		super(Properties.of(Material.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion());
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(NORTH, Connection.NONE).setValue(EAST, Connection.NONE)
				.setValue(SOUTH, Connection.NONE).setValue(WEST, Connection.NONE)
				.setValue(DOWN, false).setValue(LADDER, false));
	}

	public static EnumProperty<Connection> getConnection(Direction direction) {
		return switch (direction) {
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			case WEST -> WEST;
			default -> NORTH;
		};
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, DOWN, LADDER);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new IrrigationTankBlockEntity(pos, state);
	}

	@Nullable
	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ModBlockEntityTypes.IRRIGATION_TANK.get() ? (BlockEntityTicker<T>) (BlockEntityTicker<IrrigationComponentBlockEntity>) IrrigationComponentBlockEntity::ticker : null;
	}

	@Nullable
	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = this.defaultBlockState();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			boolean channel = context.getLevel().getBlockState(context.getClickedPos().relative(dir)).getBlock() instanceof IrrigationChannelBlock;
			state = state.setValue(getConnection(dir), channel ? Connection.CHANNEL : Connection.NONE);
		}
		return state;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.getBlockEntity(pos) instanceof IrrigationTankBlockEntity tank) {
			tank.checkAndFormMultiBlock();
		}
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level.getBlockEntity(pos) instanceof IrrigationTankBlockEntity tank)) {
			return InteractionResult.PASS;
		}
		if (stack.is(Items.WATER_BUCKET)) {
			// try to fill from a bucket
			if (tank.pushWater(1000, false) == 1000) {
				if (!level.isClientSide) {
					tank.pushWater(1000, true);
					player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
					level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
				return InteractionResult.sidedSuccess(level.isClientSide);
			}
			return InteractionResult.CONSUME;
		} else if (stack.is(Items.BUCKET)) {
			// try to drain to a bucket
			if (tank.drainWater(1000, false) == 1000) {
				if (!level.isClientSide) {
					tank.drainWater(1000, true);
					player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
					level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
				}
				return InteractionResult.sidedSuccess(level.isClientSide);
			}
			return InteractionResult.CONSUME;
		} else if (stack.is(Items.LADDER) && !state.getValue(LADDER)) {
			// try to place a ladder
			if (!level.isClientSide) {
				level.setBlock(pos, state.setValue(LADDER, true), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.LADDER_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
				if (!player.getAbilities().instabuild) {
					stack.shrink(1);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return InteractionResult.PASS;
	}

	@Override
	public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (!direction.getAxis().isHorizontal()) {
			return state;
		}
		EnumProperty<Connection> property = getConnection(direction);
		if (state.getValue(property) == Connection.TANK) {
			// tank logic is handled by the block entity
			return state;
		}
		return state.setValue(property, neighborState.getBlock() instanceof IrrigationChannelBlock ? Connection.CHANNEL : Connection.NONE);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
		super.neighborChanged(state, level, pos, block, fromPos, isMoving);
		if (level.getBlockEntity(pos) instanceof IrrigationTankBlockEntity tank) {
			tank.onNeighbourChanged(fromPos);
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof IrrigationTankBlockEntity tank) {
			// a sufficiently full block turns into water, like AgriCraft 3's tank-water fluid did
			boolean spawnWater = IrrigationConfig.tankSpawnWaterBlock && newState.isAir() && !level.dimensionType().ultraWarm()
					&& tank.getFluidLevel() - pos.getY() > 0.5F;
			tank.unFormMultiBlock(pos);
			if (spawnWater && level instanceof ServerLevel serverLevel) {
				// placing the water from inside onRemove would make the removal report failure (and skip drops)
				MinecraftServer server = serverLevel.getServer();
				server.tell(new TickTask(server.getTickCount(), () -> {
					if (serverLevel.getBlockState(pos).isAir()) {
						serverLevel.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
					}
				}));
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public void handlePrecipitation(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation) {
		int rate = IrrigationConfig.rainFillRate;
		if (precipitation == Biome.Precipitation.RAIN && rate > 0 && level.getBlockEntity(pos) instanceof IrrigationTankBlockEntity tank) {
			tank.pushWater(rate, true);
		}
	}

	@Override
	public List<ItemStack> getDrops(BlockState state, LootContext.Builder params) {
		List<ItemStack> drops = super.getDrops(state, params);
		drops.add(new ItemStack(ModItems.IRRIGATION_TANK.get()));
		if (state.getValue(LADDER)) {
			drops.add(new ItemStack(Items.LADDER));
		}
		return drops;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state);
	}

	public static VoxelShape getShape(BlockState state) {
		if (!(state.getBlock() instanceof IrrigationTankBlock)) {
			return Shapes.empty();
		}
		return SHAPES.computeIfAbsent(state, aState -> Stream.concat(
						Direction.Plane.HORIZONTAL.stream().map(dir -> aState.getValue(getConnection(dir)).getShape(dir)),
						Stream.of(aState.getValue(DOWN) ? Shapes.empty() : SHAPE_DOWN))
				.reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR))
				.map(VoxelShape::optimize)
				.orElse(Shapes.block()));
	}

	public enum Connection implements StringRepresentable {
		NONE(Block.box(0, 0, 0, 16, 16, 2), Block.box(0, 0, 0, 2, 16, 16)),
		TANK(Shapes.empty(), Shapes.empty()),
		CHANNEL(
				Stream.of(
						Block.box(0, 0, 0, 16, 6, 2),
						Block.box(0, 6, 0, 6, 10, 2),
						Block.box(10, 6, 0, 16, 10, 2),
						Block.box(0, 10, 0, 16, 16, 2)
				).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get(),
				Stream.of(
						Block.box(0, 0, 0, 2, 6, 16),
						Block.box(0, 6, 0, 2, 10, 6),
						Block.box(0, 6, 10, 2, 10, 16),
						Block.box(0, 10, 0, 2, 16, 16)
				).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get()
		);

		private final VoxelShape north;
		private final VoxelShape east;
		private final VoxelShape south;
		private final VoxelShape west;

		Connection(VoxelShape north, VoxelShape west) {
			this.north = north;
			this.south = north.move(0, 0, 14 / 16.0);
			this.west = west;
			this.east = west.move(14 / 16.0, 0, 0);
		}

		public VoxelShape getShape(Direction direction) {
			return switch (direction) {
				case NORTH -> this.north;
				case SOUTH -> this.south;
				case EAST -> this.east;
				case WEST -> this.west;
				default -> Shapes.empty();
			};
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

}
