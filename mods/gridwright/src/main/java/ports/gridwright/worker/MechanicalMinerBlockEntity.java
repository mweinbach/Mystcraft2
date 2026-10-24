package ports.gridwright.worker;

import ports.gridwright.block.MechanicalBlock;
import ports.gridwright.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The mechanical miner breaks the block in front of it as its owner would, holding the tool in its
 * tool slot (or bare-handed): the block takes as long as it would take that player, drops only what
 * that tool harvests, and the tool wears and can break. Drops go to its output; while they do not fit,
 * it waits. It needs {@link #GP} Grid Power while working and pauses on a redstone signal.
 *
 * <p>Each break goes through {@link MachinePlayer#mayBreak}, so it cannot mine where its owner may
 * not. Like the quarries it leaves air, fluids, unbreakable and {@code #gridwright:unminable} blocks
 * and blocks with block entities alone.
 */
public class MechanicalMinerBlockEntity extends WorkerBlockEntity {

    public static final double GP = 4;

    private float progress;
    private BlockState target = Blocks.AIR.defaultBlockState();
    private int broken;

    public MechanicalMinerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_MINER.get(), pos, state, WorkerLayout.MECHANICAL_MINER);
    }

    @Override
    protected int progressPerMille() {
        return (int) (progress * 1000);
    }

    @Override
    protected int counter() {
        return broken;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof MechanicalMinerBlockEntity miner) miner.tick(server);
    }

    private void tick(ServerLevel level) {
        if (level.hasNeighborSignal(worldPosition)) {
            stop(Status.PAUSED);
            return;
        }
        Direction facing = getBlockState().getValue(MechanicalBlock.FACING);
        BlockPos pos = worldPosition.relative(facing);
        BlockState state = level.getBlockState(pos);
        if (state != target) {
            target = state;
            progress = 0;
        }
        if (state.hasBlockEntity() || !MachinePlayer.minable(level, pos, state)) {
            stop(Status.IDLE);
            return;
        }
        if (!requestPower(level, GP)) return;
        ServerPlayer player = MachinePlayer.prepare(level, this, facing);
        if (player == null) {
            stop(Status.NO_OWNER);
            return;
        }
        int toolSlot = layout().first(WorkerLayout.Role.TOOL);
        ItemStack tool = items.get(toolSlot);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        try {
            progress = Math.min(1, progress + state.getDestroyProgress(player, level, pos));
            if (progress < 1) return;
            if (!MachinePlayer.mayBreak(level, pos, state, player)) {
                stop(Status.PROTECTED);
                progress = 0;
                return;
            }
            List<ItemStack> drops = player.hasCorrectToolForDrops(state)
                    ? Block.getDrops(state, level, pos, null, player, tool) : List.of();
            int outputs = layout().firstOutput();
            if (!fits(drops, outputs, layout().size())) {
                stop(Status.BLOCKED);
                return;
            }
            MachinePlayer.destroy(level, pos, state, null, player, tool, true);
            if (tool.isEmpty()) items.set(toolSlot, ItemStack.EMPTY);
            drops.forEach(stack -> store(stack, outputs, layout().size()));
            progress = 0;
            broken++;
        } finally {
            player.getInventory().clearContent();
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Broken", broken);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        broken = tag.getInt("Broken");
    }
}
