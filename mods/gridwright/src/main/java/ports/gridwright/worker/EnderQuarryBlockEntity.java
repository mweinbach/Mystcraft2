package ports.gridwright.worker;

import ports.gridwright.machine.EnergyBuffer;
import ports.gridwright.machine.EnergyHolder;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The ender quarry mines real terrain inside a loop of fences or walls ({@link QuarryArea}), layer by
 * layer from the quarry's height down to the bottom of the world. It runs on Forge Energy or Team
 * Reborn Energy (it accepts up to {@link #MAX_RECEIVE} per transfer and stores {@link #CAPACITY}) and
 * spends {@link #energyPerBlock} on every block it breaks.
 *
 * <p>Every break is made by the owner's fake player and must pass {@link MachinePlayer#mayBreak}, so
 * claimed or protected blocks are skipped rather than mined. It never mines air, fluids, unbreakable
 * blocks, {@code #gridwright:unminable} or blocks with block entities (chests, spawners, other
 * machines), and leaves the hole open. It works only while the chunks it mines are loaded. The
 * boundary is read when the quarry first finds it; break and replace the quarry to read it again.
 */
public class EnderQuarryBlockEntity extends WorkerBlockEntity implements EnergyHolder {

    public static final long CAPACITY = 100_000;
    public static final long MAX_RECEIVE = 10_000;
    public static final long BASE_ENERGY = 400;
    public static final long SPEED_ENERGY = 100;
    public static final long FORTUNE_ENERGY = 200;
    public static final long SILK_ENERGY = 200;
    /** Blocks mined per tick with no speed upgrades; each speed level adds as much again. */
    public static final double BASE_RATE = 0.25;
    /** Positions checked per tick at most, so empty air above the ground passes quickly but boundedly. */
    public static final int MAX_SCAN = 256;
    private static final int RESCAN_TICKS = 20;

    private final EnergyBuffer energy = new EnergyBuffer(CAPACITY, 0, MAX_RECEIVE);
    @Nullable private QuarryArea area;
    private int column;
    private int layer;
    private boolean finished;
    private long mined;
    private double budget;
    private int rescan;
    @Nullable private List<ItemStack> pending;

    public EnderQuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENDER_QUARRY.get(), pos, state, WorkerLayout.ENDER_QUARRY);
        energy.onChange(this::setChanged);
    }

    @Override
    public EnergyBuffer energy() {
        return energy;
    }

    @Nullable
    public QuarryArea area() {
        return area;
    }

    public long mined() {
        return mined;
    }

    @Override
    public List<ItemStack> pendingDrops() {
        return pending == null ? List.of() : pending;
    }

    public static long energyPerBlock(Upgrades upgrades) {
        return BASE_ENERGY + SPEED_ENERGY * upgrades.speed() + FORTUNE_ENERGY * upgrades.fortune()
                + (upgrades.silkTouch() ? SILK_ENERGY : 0);
    }

    public static double rate(Upgrades upgrades) {
        return BASE_RATE * (1 + upgrades.speed());
    }

    @Override
    protected int counter() {
        return (int) Math.min(Integer.MAX_VALUE, mined);
    }

    @Override
    protected int progressPerMille() {
        if (area == null || level == null) return 0;
        if (finished) return 1000;
        int height = area.y() - level.getMinBuildHeight() + 1;
        return (area.y() - layer) * 1000 / Math.max(1, height);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level instanceof ServerLevel server && blockEntity instanceof EnderQuarryBlockEntity quarry) quarry.tick(server);
    }

    private void tick(ServerLevel level) {
        if (owner() == null) {
            stop(Status.NO_OWNER);
            return;
        }
        if (area == null && !findArea(level)) return;
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
        if (finished) {
            stop(Status.FINISHED);
            return;
        }
        Upgrades upgrades = Upgrades.read(this, layout());
        double rate = rate(upgrades);
        budget = Math.min(budget + rate, Math.max(1, rate));
        status = Status.RUNNING;
        ItemStack tool = upgrades.tool();
        long cost = energyPerBlock(upgrades);
        ServerPlayer player = null;
        for (int scanned = 0; budget >= 1 && scanned < MAX_SCAN && !finished; scanned++) {
            BlockPos pos = area.pos(column, layer);
            if (!level.isLoaded(pos)) {
                status = Status.WAITING;
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!worthMining(level, pos, state)) {
                advance(level);
                continue;
            }
            if (energy.stored() < cost) {
                status = Status.NO_ENERGY;
                break;
            }
            if (player == null) {
                player = MachinePlayer.prepare(level, this, Direction.DOWN);
                if (player == null) break;
                player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            }
            if (!MachinePlayer.mayBreak(level, pos, state, player)) {
                advance(level);
                continue;
            }
            List<ItemStack> drops = state.requiresCorrectToolForDrops() && !tool.isCorrectToolForDrops(state)
                    ? List.of() : Block.getDrops(state, level, pos, null, player, tool);
            MachinePlayer.destroy(level, pos, state, null, player, tool, false);
            energy.consume(cost);
            mined++;
            budget -= 1;
            advance(level);
            if (fits(drops, outputs, layout().size())) drops.forEach(stack -> store(stack, outputs, layout().size()));
            else {
                pending = new ArrayList<>(drops);
                break;
            }
        }
        if (player != null) player.getInventory().clearContent();
        if (finished) status = Status.FINISHED;
        setChanged();
    }

    /** Looks for a boundary once a second; returns whether there is an area to mine. */
    private boolean findArea(ServerLevel level) {
        if (rescan > 0) {
            rescan--;
            return false;
        }
        rescan = RESCAN_TICKS;
        QuarryArea.Scan scan = QuarryArea.scan(level, worldPosition);
        if (scan.area() == null) {
            stop(scan.status());
            return false;
        }
        area = scan.area();
        layer = area.y();
        column = area.interior().nextSetBit(0);
        finished = false;
        setChanged();
        return true;
    }

    private boolean worthMining(ServerLevel level, BlockPos pos, BlockState state) {
        return !pos.equals(worldPosition) && !state.hasBlockEntity() && MachinePlayer.minable(level, pos, state);
    }

    /** Moves to the next interior column, and down a layer after the last one. */
    private void advance(ServerLevel level) {
        column = area.interior().nextSetBit(column + 1);
        if (column >= 0) return;
        column = area.interior().nextSetBit(0);
        layer--;
        if (layer < level.getMinBuildHeight()) finished = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("Energy", energy.stored());
        tag.putLong("Mined", mined);
        if (area != null) {
            tag.put("Area", area.save());
            tag.putInt("Column", column);
            tag.putInt("Layer", layer);
            tag.putBoolean("Finished", finished);
        }
        if (pending != null) tag.put("Pending", saveStacks(pending));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy.restore(tag.getLong("Energy"));
        mined = tag.getLong("Mined");
        area = tag.contains("Area") ? QuarryArea.load(tag.getCompound("Area")) : null;
        column = tag.getInt("Column");
        layer = tag.getInt("Layer");
        finished = tag.getBoolean("Finished");
        pending = tag.contains("Pending") ? loadStacks(tag.getList("Pending", 10)) : null;
    }
}
