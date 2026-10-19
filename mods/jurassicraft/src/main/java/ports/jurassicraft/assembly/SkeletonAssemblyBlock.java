package ports.jurassicraft.assembly;

import ports.jurassicraft.platform.Platform;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;

/** Two adjacent blocks form the original wide assembly table. */
public final class SkeletonAssemblyBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;

    public SkeletonAssemblyBlock() {
        super(BlockBehaviour.Properties.of(Material.WOOD).strength(2.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, PART); }
    private static Direction otherDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(PART) == BedPart.FOOT ? facing.getCounterClockWise() : facing.getClockWise();
    }
    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        BlockPos other = context.getClickedPos().relative(otherDirection(state));
        return context.getLevel().getWorldBorder().isWithinBounds(other) && context.getLevel().getBlockState(other).canBeReplaced(context) ? state : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && state.getValue(PART) == BedPart.FOOT) level.setBlock(pos.relative(otherDirection(state)), state.setValue(PART, BedPart.HEAD), 3);
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == otherDirection(state)) {
            if (!neighbour.is(this) || neighbour.getValue(PART) == state.getValue(PART) || neighbour.getValue(FACING) != state.getValue(FACING)) return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState rotated = state.rotate(mirror.getRotation(state.getValue(FACING)));
        if (mirror == Mirror.NONE) return rotated;
        return rotated.setValue(PART, state.getValue(PART) == BedPart.FOOT ? BedPart.HEAD : BedPart.FOOT);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos main = state.getValue(PART) == BedPart.FOOT ? pos : pos.relative(otherDirection(state));
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            Platform.openMenu(server, new SimpleMenuProvider((id, inventory, p) -> new SkeletonAssemblyMenu(id, inventory, main),
                    Component.translatable("container.jurassicraft.skeleton_assembly")), main);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
