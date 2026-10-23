package ports.buildcraft.transport.flow;

import java.math.BigInteger;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.buildcraft.energy.EngineBlockEntity;
import ports.buildcraft.lib.Ports;
import ports.buildcraft.lib.Power;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.transport.PipeBlockEntity;

/**
 * Upstream {@code PipeFlowPower} (kinesis pipes). Each face section holds power received this tick
 * and the power its neighbours asked for; power is shared out in proportion to those requests one
 * tick later. Power is counted in micro-MJ; neighbouring blocks see Forge Energy or Team Reborn
 * Energy units at {@link Power#UNITS_PER_MJ} per MJ. Transfer is lossless, as upstream's default: upstream
 * {@code BCTransportConfig} gives each pipe a resistance (1/32 MJ per MJ for the iron and diamond pipes) and
 * {@code reconfigure} works out a loss from it, but its transfer never subtracts that loss and its
 * {@code kinesisLossMode} defaults to {@code lossless}.
 */
public final class PowerFlow extends PipeFlow {
    private static final int SYNC_INTERVAL = 2;
    /** Upstream {@code DEFAULT_MAX_POWER}, used when a configured limit is not positive. */
    private static final long DEFAULT_MAX_POWER = 10 * Power.MJ;

    private final boolean isReceiver;
    private final Section[] sections = new Section[6];
    private long currentWorldTime;
    private long lastSync;

    public PowerFlow(PipeBlockEntity pipe) {
        super(pipe);
        isReceiver = pipe.definition().powerReceiver;
        for (Direction face : Direction.values()) sections[face.ordinal()] = new Section(face);
    }

    /**
     * Upstream {@code reconfigure}: the definition's rate as the behaviour configures it (a limiter shifts it).
     * Read from the behaviour each time, so a changed limit applies at once.
     */
    public long maxPower() {
        long max = pipe.behaviour().configureMaxPower(pipe.definition().powerTransferPerTick);
        return max <= 0 ? DEFAULT_MAX_POWER : max;
    }

    /** Upstream {@code PipeEventPower.Configure#disableTransfer}: the pipe asks its neighbouring pipes for nothing. */
    public boolean isTransferDisabled() {
        return pipe.behaviour().disablesPowerTransfer();
    }

    /** Micro-MJ held in the pipe's sections, including what arrived this tick; for checks that no power is made or lost. */
    public long storedMicroJoules() {
        long stored = 0;
        for (Section s : sections) stored += s.internalPower + s.internalNextPower;
        return stored;
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof PowerFlow;
    }

    @Override
    public boolean canConnectTile(Direction face, BlockEntity tile) {
        if (tile instanceof EngineBlockEntity engine) return engine.outputsTo(face.getOpposite());
        return Ports.energy(pipe.getLevel(), tile.getBlockPos(), face.getOpposite()) != null;
    }

    @Override
    public void serverTick() {
        Flow[] lastFlows = new Flow[6];
        int[] lastDisplayPower = new int[6];
        for (Section s : sections) {
            lastFlows[s.side.ordinal()] = s.displayFlow;
            lastDisplayPower[s.side.ordinal()] = s.displayPower;
        }
        step();
        long maxPower = maxPower();
        for (Section s : sections) {
            if (s.internalPower <= 0) continue;
            long totalPowerQuery = 0;
            for (Section s2 : sections) {
                if (s2 != s) totalPowerQuery += s2.powerQuery;
            }
            boolean returnPower = false;
            if (totalPowerQuery <= 0 && s.powerQuery > 0) {
                totalPowerQuery = s.powerQuery;
                returnPower = true;
            }
            if (totalPowerQuery <= 0) continue;
            long unusedPowerQuery = totalPowerQuery;
            for (Section s2 : sections) {
                if (s2 == s && !returnPower) continue;
                if (s2.powerQuery <= 0) continue;
                long watts = Math.min(BigInteger.valueOf(s.internalPower).multiply(BigInteger.valueOf(s2.powerQuery))
                        .divide(BigInteger.valueOf(unusedPowerQuery)).longValue(), s.internalPower);
                unusedPowerQuery -= s2.powerQuery;
                long leftover = watts;
                PowerFlow neighbour = neighbourFlow(s2.side);
                if (neighbour != null) {
                    leftover = neighbour.sections[s2.side.getOpposite().ordinal()].receivePowerInternal(watts);
                } else {
                    EnergyPort receiver = receiver(s2.side);
                    if (receiver != null) leftover = watts - Power.fromUnits(receiver.receive(Power.toUnits(watts), false));
                }
                long used = watts - leftover;
                s.internalPower -= used;
                s.powerTick += used;
                s2.powerTick += used;
                s.displayFlow = Flow.OUT;
                s2.displayFlow = Flow.IN;
            }
        }
        for (Section s : sections) {
            double value = Math.sqrt(s.powerTick / (double) maxPower);
            s.displayPower = (int) (Math.min(1, value) * Power.MJ);
            s.powerTick = 0;
        }
        for (Direction face : Direction.values()) {
            if (pipe.connectedType(face) != PipeBlockEntity.ConnectedType.TILE) continue;
            EnergyPort receiver = receiver(face);
            if (receiver == null) continue;
            long requested = Power.fromUnits(receiver.receive(Power.toUnits(maxPower), true));
            if (requested > 0) requestPower(face, requested);
        }
        long[] transferQuery = new long[6];
        for (Direction face : Direction.values()) {
            if (!pipe.isConnected(face)) continue;
            long query = 0;
            for (Section s2 : sections) {
                if (s2.side != face) query += s2.powerQuery;
            }
            transferQuery[face.ordinal()] = query;
        }
        boolean disabled = isTransferDisabled();
        for (Direction face : Direction.values()) {
            if (disabled || transferQuery[face.ordinal()] <= 0) continue;
            PowerFlow other = neighbourFlow(face);
            if (other != null) other.requestPower(face.getOpposite(), transferQuery[face.ordinal()]);
        }
        boolean changed = false;
        for (Section s : sections) {
            int i = s.side.ordinal();
            if (lastFlows[i] != s.displayFlow || lastDisplayPower[i] != s.displayPower) changed = true;
        }
        long now = pipe.getLevel().getGameTime();
        if (changed && now - lastSync >= SYNC_INTERVAL) {
            lastSync = now;
            pipe.queueSync();
        }
    }

    @Nullable
    private PowerFlow neighbourFlow(Direction face) {
        if (pipe.connectedType(face) != PipeBlockEntity.ConnectedType.PIPE) return null;
        if (pipe.neighbourTile(face) instanceof PipeBlockEntity other && other.flow() instanceof PowerFlow flow
                && other.isConnected(face.getOpposite())) {
            return flow;
        }
        return null;
    }

    @Nullable
    private EnergyPort receiver(Direction face) {
        if (pipe.connectedType(face) != PipeBlockEntity.ConnectedType.TILE) return null;
        EnergyPort port = Ports.energy(pipe.getLevel(), pipe.getBlockPos().relative(face), face.getOpposite());
        return port != null && port.canReceive() ? port : null;
    }

    private void step() {
        long now = pipe.getLevel().getGameTime();
        if (currentWorldTime != now) {
            currentWorldTime = now;
            for (Section s : sections) s.step();
        }
    }

    private void requestPower(Direction from, long amount) {
        step();
        Section s = sections[from.ordinal()];
        s.nextPowerQuery = Math.min(s.nextPowerQuery + amount, maxPower());
    }

    /** The power asked for through every face but {@code side}, or through all of them. */
    public long getPowerRequested(@Nullable Direction side) {
        long requested = 0;
        for (Section s : sections) {
            if (side == null || s.side != side) requested += s.powerQuery;
        }
        return requested;
    }

    @Nullable
    @Override
    public EnergyPort energyPort(Direction side) {
        return sections[side.ordinal()];
    }

    @Override
    public void save(CompoundTag tag) {
        long[] internal = new long[6];
        for (Section s : sections) internal[s.side.ordinal()] = s.internalPower + s.internalNextPower;
        tag.putLongArray("internalPower", internal);
    }

    @Override
    public void load(CompoundTag tag) {
        long[] internal = tag.getLongArray("internalPower");
        if (internal.length != 6) return;
        for (Section s : sections) s.internalPower = internal[s.side.ordinal()];
    }

    @Override
    public void writeClient(CompoundTag tag) {
        int[] power = new int[6];
        int[] flow = new int[6];
        for (Section s : sections) {
            power[s.side.ordinal()] = s.displayPower;
            flow[s.side.ordinal()] = s.displayFlow.value;
        }
        tag.putIntArray("displayPower", power);
        tag.putIntArray("displayFlow", flow);
    }

    @Override
    public void readClient(CompoundTag tag) {
        int[] power = tag.getIntArray("displayPower");
        int[] flow = tag.getIntArray("displayFlow");
        if (power.length != 6 || flow.length != 6) return;
        for (Section s : sections) {
            s.displayPower = power[s.side.ordinal()];
            s.displayFlow = flow[s.side.ordinal()] < 0 ? Flow.IN : flow[s.side.ordinal()] > 0 ? Flow.OUT : Flow.STATIONARY;
        }
    }

    @Override
    public void clientTick() {
        for (Section s : sections) {
            s.clientDisplayFlowLast = s.clientDisplayFlow;
            double diff = s.displayFlow.value * 2.4 * s.side.getAxisDirection().getStep();
            s.clientDisplayFlow = (s.clientDisplayFlow + 16 + diff) % 16;
        }
    }

    /** Display power of a face in [0, 1], as upstream's square-root scale of the transferred power. */
    public double displayPower(Direction side) {
        return sections[side.ordinal()].displayPower / (double) Power.MJ;
    }

    /** Animated texture offset of a face in pixels, interpolated. */
    public double displayFlow(Direction side, float partialTicks) {
        Section s = sections[side.ordinal()];
        double last = s.clientDisplayFlowLast;
        double now = s.clientDisplayFlow;
        if (Math.abs(now - last) > 8) last += now > last ? 16 : -16;
        return last + (now - last) * partialTicks;
    }

    enum Flow {
        IN(-1), OUT(1), STATIONARY(0);

        final int value;

        Flow(int value) {
            this.value = value;
        }
    }

    /** One face. Only wooden kinesis pipes accept power from neighbouring blocks. */
    private final class Section implements EnergyPort {
        final Direction side;
        long powerQuery;
        long nextPowerQuery;
        long internalPower;
        long internalNextPower;
        long powerTick;
        int displayPower;
        Flow displayFlow = Flow.STATIONARY;
        double clientDisplayFlow;
        double clientDisplayFlowLast;

        Section(Direction side) {
            this.side = side;
            clientDisplayFlow = (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 7 : 1) / 8.0;
        }

        void step() {
            powerQuery = nextPowerQuery;
            nextPowerQuery = 0;
            internalPower += internalNextPower;
            internalNextPower = 0;
        }

        long receivePowerInternal(long sent) {
            if (sent <= 0) return sent;
            PowerFlow.this.step();
            internalNextPower += sent;
            return 0;
        }

        /**
         * Upstream sections accept any amount; here a receiving section takes no more than its
         * neighbours requested, so simulated and real calls agree and transactional callers can rely
         * on the simulated result.
         */
        @Override
        public long receive(long amount, boolean simulate) {
            if (!isReceiver || amount <= 0 || !pipe.hasLevel()) return 0;
            PowerFlow.this.step();
            long accepted = Math.min(amount, Power.toUnits(getPowerRequested(side)));
            if (!simulate && accepted > 0) {
                receivePowerInternal(Power.fromUnits(accepted));
                pipe.setChanged();
            }
            return accepted;
        }

        @Override public long extract(long amount, boolean simulate) { return 0; }
        @Override public long stored() { return Power.toUnits(internalPower + internalNextPower); }
        @Override public long capacity() { return Power.toUnits(maxPower()); }
        @Override public boolean canReceive() { return isReceiver; }
        @Override public boolean canExtract() { return false; }
        @Override public Object snapshot() { return new long[] {internalNextPower, internalPower}; }

        @Override
        public void restore(Object snapshot) {
            long[] values = (long[]) snapshot;
            internalNextPower = values[0];
            internalPower = values[1];
        }
    }
}
