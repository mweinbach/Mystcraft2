package ports.gridwright.block;

import ports.gridwright.platform.Platform;
import ports.gridwright.registry.ModBlockEntities;
import ports.gridwright.worker.EnderQuarryBlockEntity;
import ports.gridwright.worker.MechanicalCrafterBlockEntity;
import ports.gridwright.worker.MechanicalMinerBlockEntity;
import ports.gridwright.worker.MechanicalUserBlockEntity;
import ports.gridwright.worker.QuantumQuarryBlockEntity;
import ports.gridwright.worker.WorkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/** A quarry, the mechanical crafter or (through {@link MechanicalBlock}) a mechanical miner or user: a worker with a screen. */
public class WorkerBlock extends GridMemberBlock {

    public enum Kind { QUANTUM_QUARRY, ENDER_QUARRY, MECHANICAL_MINER, MECHANICAL_USER, MECHANICAL_CRAFTER }

    private final Kind kind;

    public WorkerBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case QUANTUM_QUARRY -> new QuantumQuarryBlockEntity(pos, state);
            case ENDER_QUARRY -> new EnderQuarryBlockEntity(pos, state);
            case MECHANICAL_MINER -> new MechanicalMinerBlockEntity(pos, state);
            case MECHANICAL_USER -> new MechanicalUserBlockEntity(pos, state);
            case MECHANICAL_CRAFTER -> new MechanicalCrafterBlockEntity(pos, state);
        };
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return switch (kind) {
            case QUANTUM_QUARRY -> createTickerHelper(type, ModBlockEntities.QUANTUM_QUARRY.get(), QuantumQuarryBlockEntity::serverTick);
            case ENDER_QUARRY -> createTickerHelper(type, ModBlockEntities.ENDER_QUARRY.get(), EnderQuarryBlockEntity::serverTick);
            case MECHANICAL_MINER -> createTickerHelper(type, ModBlockEntities.MECHANICAL_MINER.get(), MechanicalMinerBlockEntity::serverTick);
            case MECHANICAL_USER -> createTickerHelper(type, ModBlockEntities.MECHANICAL_USER.get(), MechanicalUserBlockEntity::serverTick);
            case MECHANICAL_CRAFTER -> createTickerHelper(type, ModBlockEntities.MECHANICAL_CRAFTER.get(),
                    MechanicalCrafterBlockEntity::serverTick);
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof WorkerBlockEntity worker) {
            Platform.openMenu(serverPlayer, worker, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof WorkerBlockEntity worker) {
                Containers.dropContents(level, pos, worker);
                for (ItemStack stack : worker.pendingDrops()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
}
