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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The mechanical miner breaks the block in front of it as its owner would, holding the tool in its
 * tool slot (or bare-handed): the block takes as long as it would take that player, drops only what
 * that tool harvests, and the tool wears and can break. Drops go to its output; any that do not fit
 * are held (and saved) and it waits until they do. It needs {@link #GP} Grid Power while working and
 * pauses on a redstone signal. Changing the tool (or its enchantments) part-way restarts the break, so
 * the tool that finishes a block is the one that did all the work.
 *
 * <p>Each break goes through {@link MachinePlayer#mayBreak}, so it cannot mine where its owner may
 * not. Like the quarries it leaves air, fluids, unbreakable and {@code #gridwright:unminable} blocks
 * and blocks with block entities alone.
 */
public class MechanicalMinerBlockEntity extends WorkerBlockEntity {

    public static final double GP = 4;

    private float progress;
    private BlockState target = Blocks.AIR.defaultBlockState();
    private ItemStack targetTool = ItemStack.EMPTY;
    private int broken;
    @Nullable private List<ItemStack> pending;

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
        int outputs = layout().firstOutput();
        if (pending != null) {
            if (!fits(pending, outputs, layout().size())) {
                stop(Status.BLOCKED);
                return;
            }
            pending.forEach(stack -> store(stack, outputs, layout().size()));
            pending = null;
            setChanged();
        }
        Direction facing = getBlockState().getValue(MechanicalBlock.FACING);
        BlockPos pos = worldPosition.relative(facing);
        BlockState state = level.getBlockState(pos);
        int toolSlot = layout().first(WorkerLayout.Role.TOOL);
        ItemStack tool = items.get(toolSlot);
        // Progress belongs to one block and one tool (enchantments included): a swap starts over.
        if (state != target || !ItemStack.isSameItemSameTags(tool, targetTool)) {
            target = state;
            targetTool = tool.copy();
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
            MachinePlayer.destroy(level, pos, state, null, player, tool, true);
            if (tool.isEmpty()) items.set(toolSlot, ItemStack.EMPTY);
            if (fits(drops, outputs, layout().size())) drops.forEach(stack -> store(stack, outputs, layout().size()));
            else pending = new ArrayList<>(drops);
            progress = 0;
            broken++;
        } finally {
            player.getInventory().clearContent();
            setChanged();
        }
    }

    @Override
    public List<ItemStack> pendingDrops() {
        return pending == null ? List.of() : pending;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Broken", broken);
        if (pending != null) tag.put("Pending", saveStacks(pending));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        broken = tag.getInt("Broken");
        pending = tag.contains("Pending") ? loadStacks(tag.getList("Pending", 10)) : null;
    }
}
