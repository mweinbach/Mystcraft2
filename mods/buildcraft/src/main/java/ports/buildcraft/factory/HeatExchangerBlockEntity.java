package ports.buildcraft.factory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.energy.RefineryRecipes;
import ports.buildcraft.factory.HeatExchangerBlock.Part;
import ports.buildcraft.lib.ClientTank;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Ports;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.lib.Tank;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.platform.PortHolder;

/**
 * Upstream {@code TileHeatExchange}. The start of a formed row heats the fluid piped into its bottom
 * using the coolant piped into the side of the end, moving the same amount of each per tick: 5, 10 or
 * 20 mB for one, two or three middle sections. Heated fluid leaves the top of the end and cooled fluid
 * leaves the outer side of the start. Exchange begins after a 120-tick warm-up.
 */
public class HeatExchangerBlockEntity extends SyncedBlockEntity implements PortHolder {
    public static final int CAPACITY = 2000;
    public static final int MIN_LENGTH = 3, MAX_LENGTH = 5;
    /** Upstream {@code FLUID_MULT}: mB moved per tick for each number of middle sections. */
    private static final int[] FLUID_MULT = {5, 10, 20};
    public static final int WARM_UP_TICKS = 120;
    private static final int SYNC_INTERVAL = 10;

    public enum ProgressState { OFF, PREPARING, RUNNING, STOPPING }

    final Tank tankInput = new Tank("input", CAPACITY, this::acceptsInput);
    final Tank tankOutput = new Tank("output", CAPACITY);
    private final FluidPort[] ports = new FluidPort[7];

    private boolean checkNeighbours = true;
    private int middleCount;
    private int progress;
    private int progressLast;
    private ProgressState progressState = ProgressState.OFF;
    private ProgressState lastSentState = ProgressState.OFF;
    private long syncedState;
    private int ticksSinceSync;

    final ClientTank clientInput = new ClientTank(CAPACITY);
    final ClientTank clientOutput = new ClientTank(CAPACITY);

    public HeatExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(BCFactory.HEAT_EXCHANGER_TYPE.get(), pos, state);
        for (int i = 0; i < ports.length; i++) ports[i] = new SidePort(i == 6 ? null : Direction.from3DDataValue(i));
    }

    public Part part() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof HeatExchangerBlock ? state.getValue(HeatExchangerBlock.PART) : Part.MIDDLE;
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof HeatExchangerBlock ? state.getValue(HeatExchangerBlock.FACING) : Direction.WEST;
    }

    public Tank inputTank() { return tankInput; }
    public Tank outputTank() { return tankOutput; }
    public int middleCount() { return middleCount; }
    public ProgressState progressState() { return progressState; }
    public ClientTank inputForRender() { return clientInput; }
    public ClientTank outputForRender() { return clientOutput; }

    /** Upstream {@code getProgress}: the warm-up fraction, 0 when off and 1 while running. */
    public double progress(float partialTicks) {
        return (progressLast + (progress - progressLast) * partialTicks) / WARM_UP_TICKS;
    }

    public void checkNeighbours() {
        checkNeighbours = true;
    }

    private boolean acceptsInput(Fluid fluid) {
        return switch (part()) {
            case START -> RefineryRecipes.heatable(fluid) != null;
            case END -> RefineryRecipes.coolable(fluid) != null;
            case MIDDLE -> false;
        };
    }

    void serverTick() {
        if (checkNeighbours) {
            checkNeighbours = false;
            formRow();
        }
        if (part() == Part.START) tickStart();

        ticksSinceSync++;
        long state = syncState();
        if (progressState != lastSentState || (state != syncedState && ticksSinceSync >= SYNC_INTERVAL)) {
            lastSentState = progressState;
            syncedState = state;
            ticksSinceSync = 0;
            queueSync();
        }
        flushSync();
    }

    void clientTick() {
        clientInput.tick();
        clientOutput.tick();
        if (part() != Part.START) return;
        updateProgress();
        if (progressState == ProgressState.RUNNING && level != null && level.getBlockEntity(endPos()) instanceof HeatExchangerBlockEntity end) {
            if (end.clientInput.fluid() == Fluids.LAVA) spewForth(worldPosition, HeatExchangerBlock.toStart(facing()), ParticleTypes.LARGE_SMOKE);
            if (clientInput.fluid() == Fluids.WATER) spewForth(end.worldPosition, Direction.UP, ParticleTypes.CLOUD);
        }
    }

    /** Upstream {@code findAdjacentExchangers}: this exchanger and up to five either side facing the same way, start first. */
    private Deque<HeatExchangerBlockEntity> findRow() {
        Deque<HeatExchangerBlockEntity> row = new ArrayDeque<>();
        row.add(this);
        if (level == null) return row;
        Direction facing = facing();
        for (int i = 1; i <= MAX_LENGTH; i++) {
            if (!(level.getBlockEntity(worldPosition.relative(HeatExchangerBlock.toStart(facing), i)) instanceof HeatExchangerBlockEntity other)
                    || other.facing() != facing) break;
            row.addFirst(other);
        }
        for (int i = 1; i <= MAX_LENGTH; i++) {
            if (!(level.getBlockEntity(worldPosition.relative(HeatExchangerBlock.toEnd(facing), i)) instanceof HeatExchangerBlockEntity other)
                    || other.facing() != facing) break;
            row.addLast(other);
        }
        return row;
    }

    /**
     * Assigns the start and end of the row. The tanks of an existing start or end move with that role to
     * the row's new ends. Rows shorter than three are not formed and drop their fluid as fragile fluid
     * shards (upstream {@code removeSection}); as upstream, rows longer than five are left as they were.
     */
    private void formRow() {
        Deque<HeatExchangerBlockEntity> row = findRow();
        if (row.size() > MAX_LENGTH) return;
        boolean formed = row.size() >= MIN_LENGTH;
        if (!formed && level != null) {
            for (HeatExchangerBlockEntity exchanger : row) exchanger.dropTanks();
        }
        HeatExchangerBlockEntity oldStart = null, oldEnd = null;
        for (HeatExchangerBlockEntity exchanger : row) {
            exchanger.checkNeighbours = false;
            if (exchanger.part() == Part.START && oldStart == null) oldStart = exchanger;
            else if (exchanger.part() == Part.END && oldEnd == null) oldEnd = exchanger;
        }
        long[] start = oldStart == null ? null : oldStart.tankValues();
        long[] end = oldEnd == null ? null : oldEnd.tankValues();
        for (HeatExchangerBlockEntity exchanger : row) {
            Part part = !formed ? Part.MIDDLE : exchanger == row.getFirst() ? Part.START : exchanger == row.getLast() ? Part.END : Part.MIDDLE;
            long[] tanks = part == Part.START ? start : part == Part.END ? end : null;
            exchanger.setTankValues(tanks);
            exchanger.middleCount = part == Part.START ? row.size() - 2 : 0;
            exchanger.setPart(part);
        }
    }

    /** Drops both tanks as fragile fluid shards and empties them. */
    void dropTanks() {
        if (level == null || tankInput.isEmpty() && tankOutput.isEmpty()) return;
        FragileFluidShardItem.dropTanks(level, worldPosition, tankInput, tankOutput);
        tankInput.clear();
        tankOutput.clear();
        setChanged();
    }

    private long[] tankValues() {
        return new long[] {tankInput.snapshotValue(), tankOutput.snapshotValue()};
    }

    private void setTankValues(@Nullable long[] values) {
        if (values == null) {
            tankInput.clear();
            tankOutput.clear();
        } else {
            tankInput.restoreValue(values[0]);
            tankOutput.restoreValue(values[1]);
        }
        setChanged();
    }

    private void setPart(Part part) {
        if (part != Part.START) resetProgress();
        if (level != null && part() != part) level.setBlock(worldPosition, getBlockState().setValue(HeatExchangerBlock.PART, part), 3);
        queueSync();
    }

    private void resetProgress() {
        progress = progressLast = 0;
        progressState = ProgressState.OFF;
    }

    /**
     * Upstream {@code rotate}: a lone exchanger turns 90 degrees; a row turns all of its exchangers
     * around, so that the start and end, with their tanks, swap places.
     */
    public void rotate() {
        if (level == null) return;
        Deque<HeatExchangerBlockEntity> row = findRow();
        Direction facing = facing();
        if (row.size() == 1) {
            level.setBlock(worldPosition, HeatExchangerBlock.withConnections(
                    getBlockState().setValue(HeatExchangerBlock.FACING, facing.getClockWise()), level, worldPosition), 3);
        } else {
            HeatExchangerBlockEntity oldStart = null, oldEnd = null;
            for (HeatExchangerBlockEntity exchanger : row) {
                if (exchanger.part() == Part.START) oldStart = exchanger;
                else if (exchanger.part() == Part.END) oldEnd = exchanger;
            }
            long[] start = oldStart == null ? null : oldStart.tankValues();
            long[] end = oldEnd == null ? null : oldEnd.tankValues();
            for (HeatExchangerBlockEntity exchanger : row) {
                Part part = exchanger == row.getLast() && start != null ? Part.START
                        : exchanger == row.getFirst() && end != null ? Part.END : Part.MIDDLE;
                exchanger.setTankValues(part == Part.START ? start : part == Part.END ? end : null);
                exchanger.resetProgress();
                exchanger.queueSync();
                level.setBlock(exchanger.worldPosition, exchanger.getBlockState()
                        .setValue(HeatExchangerBlock.FACING, facing.getOpposite()).setValue(HeatExchangerBlock.PART, part), 3);
                exchanger.checkNeighbours = true;
            }
            for (HeatExchangerBlockEntity exchanger : row) {
                BlockState connected = HeatExchangerBlock.withConnections(exchanger.getBlockState(), level, exchanger.worldPosition);
                if (connected != exchanger.getBlockState()) level.setBlock(exchanger.worldPosition, connected, 3);
            }
        }
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, 0.6F);
    }

    public BlockPos endPos() {
        return worldPosition.relative(HeatExchangerBlock.toEnd(facing()), middleCount + 1);
    }

    @Nullable
    public HeatExchangerBlockEntity end() {
        if (level == null || middleCount <= 0) return null;
        return level.getBlockEntity(endPos()) instanceof HeatExchangerBlockEntity end && end.part() == Part.END && end.facing() == facing()
                ? end : null;
    }

    private void tickStart() {
        updateProgress();
        HeatExchangerBlockEntity end = end();
        if (end != null) craft(end);
        else if (progressState != ProgressState.OFF) progressState = ProgressState.STOPPING;
        output(end);
    }

    private void updateProgress() {
        progressLast = progress;
        switch (progressState) {
            case STOPPING -> {
                progress--;
                if (progress <= 0) {
                    progress = 0;
                    progressState = ProgressState.OFF;
                }
            }
            case PREPARING, RUNNING -> {
                progress++;
                if (progress >= WARM_UP_TICKS) {
                    progress = WARM_UP_TICKS;
                    progressState = ProgressState.RUNNING;
                }
            }
            case OFF -> {}
        }
    }

    /** Upstream {@code craft}: the coolant must start hotter than the fluid being heated. */
    private void craft(HeatExchangerBlockEntity end) {
        Tank coolIn = end.tankInput, coolOut = tankOutput, heatIn = tankInput, heatOut = end.tankOutput;
        RefineryRecipes.HeatStep cool = RefineryRecipes.coolable(coolIn.fluid());
        RefineryRecipes.HeatStep heat = RefineryRecipes.heatable(heatIn.fluid());
        if (cool == null || heat == null || cool.heatFrom() <= heat.heatFrom()) {
            progressState = ProgressState.STOPPING;
            return;
        }
        int max = FLUID_MULT[Math.min(middleCount, FLUID_MULT.length) - 1];
        int coolOutAmount = cool.out() == null ? max : coolOut.fill(cool.out(), max, true);
        int heatOutAmount = heat.out() == null ? max : heatOut.fill(heat.out(), max, true);
        int coolInAmount = (int) coolIn.drain(cool.in(), max, true).amount();
        int heatInAmount = (int) heatIn.drain(heat.in(), max, true).amount();
        int amount = Math.min(Math.min(coolOutAmount, heatOutAmount), Math.min(coolInAmount, heatInAmount));
        if (amount <= 0) {
            progressState = ProgressState.STOPPING;
        } else if (progressState == ProgressState.OFF) {
            progressState = ProgressState.PREPARING;
        } else if (progressState == ProgressState.RUNNING) {
            if (cool.out() != null) coolOut.fill(cool.out(), amount, false);
            coolIn.drain(cool.in(), amount, false);
            if (heat.out() != null) heatOut.fill(heat.out(), amount, false);
            heatIn.drain(heat.in(), amount, false);
            setChanged();
            end.setChanged();
        }
    }

    /** Upstream {@code output}: up to a bucket per tick out of the start's outer side and the end's top. */
    private void output(@Nullable HeatExchangerBlockEntity end) {
        Direction outward = HeatExchangerBlock.toStart(facing());
        push(tankOutput, worldPosition.relative(outward), outward.getOpposite());
        if (end != null) end.push(end.tankOutput, end.worldPosition.above(), Direction.DOWN);
    }

    private void push(Tank tank, BlockPos target, Direction side) {
        if (tank.isEmpty() || level == null) return;
        FluidPort port = Ports.fluids(level, target, side);
        if (port == null) return;
        long used = port.fill(tank.fluid(), Math.min(1000, tank.amount()), false);
        if (used > 0) {
            tank.drain(null, (int) used, false);
            setChanged();
        }
    }

    private void spewForth(BlockPos from, Direction direction, ParticleOptions particle) {
        if (level == null) return;
        double x = from.getX() + 0.5 + direction.getStepX(), y = from.getY() + 0.5 + direction.getStepY(), z = from.getZ() + 0.5 + direction.getStepZ();
        double mx = direction.getStepX() * 0.4, my = direction.getStepY() * 0.4, mz = direction.getStepZ() * 0.4;
        for (int i = 0; i < 5; i++) {
            double dx = mx + (level.random.nextDouble() - 0.5) * 0.1;
            double dy = my + (level.random.nextDouble() - 0.5) * 0.1;
            double dz = mz + (level.random.nextDouble() - 0.5) * 0.1;
            double interp = i / 5.0;
            x -= dx * interp;
            y -= dy * interp;
            z -= dz * interp;
            level.addParticle(particle, x, y, z, dx, dy, dz);
        }
    }

    private long syncState() {
        long hash = tankInput.snapshotValue();
        hash = hash * 31 + tankOutput.snapshotValue();
        return hash * 31 + middleCount;
    }

    @Nullable
    @Override
    public FluidPort fluidPort(@Nullable Direction side) {
        return ports[side == null ? 6 : side.get3DDataValue()];
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        // Upstream never saved the start's tanks; both ends keep theirs here.
        tag.put("input", tankInput.save());
        tag.put("output", tankOutput.save());
        tag.putInt("middleCount", middleCount);
        tag.putInt("progress", progress);
        tag.putString("state", progressState.name());
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        tankInput.load(tag.getCompound("input"));
        tankOutput.load(tag.getCompound("output"));
        middleCount = tag.getInt("middleCount");
        progress = progressLast = Math.max(0, Math.min(WARM_UP_TICKS, tag.getInt("progress")));
        progressState = parseState(tag.getString("state"));
        checkNeighbours = true;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        ClientTank.write(tag, "input", tankInput);
        ClientTank.write(tag, "output", tankOutput);
        tag.putInt("middleCount", middleCount);
        tag.putString("state", progressState.name());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        clientInput.read(tag, "input");
        clientOutput.read(tag, "output");
        middleCount = tag.getInt("middleCount");
        progressState = parseState(tag.getString("state"));
    }

    private static ProgressState parseState(String name) {
        for (ProgressState state : ProgressState.values()) if (state.name().equals(name)) return state;
        return ProgressState.OFF;
    }

    /**
     * Upstream's sided capabilities, resolved on every call because loaders cache ports per side while the
     * exchanger's role and facing change: the start fills from below and drains from its outer side, the
     * end fills from its outer side and drains from the top. The unsided face serves held containers.
     */
    private final class SidePort implements FluidPort {
        @Nullable private final Direction side;

        SidePort(@Nullable Direction side) {
            this.side = side;
        }

        private boolean fills() {
            Part part = part();
            if (part == Part.MIDDLE) return false;
            if (side == null) return true;
            return part == Part.START ? side == Direction.DOWN : side == HeatExchangerBlock.toEnd(facing());
        }

        private boolean drains() {
            Part part = part();
            if (part == Part.MIDDLE) return false;
            if (side == null) return true;
            return part == Part.START ? side == HeatExchangerBlock.toStart(facing()) : side == Direction.UP;
        }

        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            if (!fills()) return 0;
            int filled = tankInput.fill(fluid, (int) Math.min(Integer.MAX_VALUE, amount), simulate);
            if (filled > 0 && !simulate) setChanged();
            return filled;
        }

        @Override
        public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
            if (!drains()) return FluidAmount.EMPTY;
            FluidAmount drained = tankOutput.drain(filter, (int) Math.min(Integer.MAX_VALUE, amount), simulate);
            if (!drained.isEmpty() && !simulate) setChanged();
            return drained;
        }

        @Override
        public List<FluidAmount> contents() {
            if (fills() && drains()) return List.of(tankInput.contents(), tankOutput.contents());
            if (fills()) return List.of(tankInput.contents());
            if (drains()) return List.of(tankOutput.contents());
            return List.of();
        }

        @Override
        public long capacity(int index) {
            return CAPACITY;
        }

        @Override
        public Object snapshot() {
            return tankValues();
        }

        @Override
        public void restore(Object snapshot) {
            long[] values = (long[]) snapshot;
            tankInput.restoreValue(values[0]);
            tankOutput.restoreValue(values[1]);
        }

        @Override
        public void onCommitted() {
            setChanged();
        }
    }
}
