package com.technovision.advancedgenetics.api.block;

import com.technovision.advancedgenetics.api.blockentity.AbstractProcessingBlockEntity;
import com.technovision.advancedgenetics.api.blockentity.ProcessingBlockEntity;
import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public abstract class AbstractGeneticsBlock extends BaseEntityBlock {

    private final BiFunction<BlockPos, BlockState, BlockEntity> blockEntityFunction;
    private final Supplier<Integer> energyPerTick;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected AbstractGeneticsBlock(BiFunction<BlockPos, BlockState, BlockEntity> blockEntity, Supplier<Integer> energyPerTick) {
        super(Properties.of(Material.METAL).requiresCorrectToolForDrops().strength(5.0f, 6.0f).sound(SoundType.METAL));
        this.blockEntityFunction = blockEntity;
        this.energyPerTick = energyPerTick;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter world, List<Component> tooltip, TooltipFlag options) {
        tooltip.add(Component.translatable("tooltip.advancedgenetics.energy_requirement", energyPerTick.get()).withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ProcessingBlockEntity processingBlockEntity) {
                processingBlockEntity.dropContents();
                world.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, world, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityFunction.apply(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide()) return null;
        return (level, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof AbstractProcessingBlockEntity processingBlockEntity) {
                processingBlockEntity.tick();
            }
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof AbstractProcessingBlockEntity processingBlockEntity) {
            if (useTool(processingBlockEntity, world, pos, player, hand)) return InteractionResult.SUCCESS;
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, processingBlockEntity, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Applies an overclocker or crowbar to the machine.
     *
     * @return true if the held item was used.
     */
    private boolean useTool(AbstractProcessingBlockEntity processingBlockEntity, Level world, BlockPos pos, Player player, InteractionHand hand) {
        ItemStack stackInHand = player.getItemInHand(hand);
        // Use overclocker on machine
        if (stackInHand.is(ItemRegistry.OVERCLOCKER.get())) {
            if (processingBlockEntity.canOverclock()) {
                processingBlockEntity.incrementOverclock();
                if (!player.isCreative()) stackInHand.shrink(1);
                return true;
            }
        }
        // Use crowbar on machine to remove overclock
        else if (stackInHand.is(ItemRegistry.CROWBAR.get())) {
            if (processingBlockEntity.getOverclock() > 0) {
                processingBlockEntity.decrementOverclock();
                Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(ItemRegistry.OVERCLOCKER.get()));
                stackInHand.hurtAndBreak(1, player, (e) -> e.broadcastBreakEvent(hand));
                return true;
            }
        }
        return false;
    }
}
