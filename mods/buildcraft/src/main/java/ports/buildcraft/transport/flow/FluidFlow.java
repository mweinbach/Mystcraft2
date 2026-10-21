package ports.buildcraft.transport.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.lib.FluidAmount;
import ports.buildcraft.lib.Fluids2;
import ports.buildcraft.lib.Ports;
import ports.buildcraft.platform.FluidPort;
import ports.buildcraft.statement.Trigger;
import ports.buildcraft.transport.PipeBlockEntity;
import ports.buildcraft.transport.SideCheck;
import ports.buildcraft.transport.statements.BCTransportStatements;

/**
 * Upstream {@code PipeFlowFluids}: seven sections (six faces and the centre), each with a direction
 * cooldown and a delay line so fluid takes {@code transferDelay} ticks to cross a section.
 */
public final class FluidFlow extends PipeFlow {
    private static final int DIRECTION_COOLDOWN = 60;
    private static final int COOLDOWN_INPUT = -DIRECTION_COOLDOWN;
    private static final int COOLDOWN_OUTPUT = DIRECTION_COOLDOWN;
    private static final int CENTER = 6;
    private static final int NETWORK_UPDATE_RATE = 10;

    private final int transferPerTick;
    private final int transferDelay;
    public final int capacity;
    private final Section[] sections = new Section[7];
    private Fluid currentFluid = Fluids.EMPTY;
    private long lastSyncTime = -NETWORK_UPDATE_RATE;

    private Fluid clientFluid = Fluids.EMPTY;
    private long lastMessage;
    private long lastMessageMinus1;

    public FluidFlow(PipeBlockEntity pipe) {
        super(pipe);
        transferPerTick = pipe.definition().fluidTransferPerTick;
        transferDelay = Math.max(1, pipe.definition().fluidTransferDelay);
        capacity = Math.max(1000, transferPerTick * 10);
        for (int i = 0; i < 7; i++) sections[i] = new Section(i == CENTER ? null : Direction.from3DDataValue(i));
        setFluid(Fluids.EMPTY);
    }

    private Section section(@Nullable Direction face) {
        return sections[face == null ? CENTER : face.get3DDataValue()];
    }

    private void setFluid(Fluid fluid) {
        currentFluid = fluid;
        for (Section section : sections) {
            section.incoming = new int[transferDelay];
            section.incomingTotalCache = 0;
            section.currentTime = 0;
            section.ticksInDirection = 0;
        }
    }

    public Fluid fluid() {
        return currentFluid;
    }

    public int totalAmount() {
        int total = 0;
        for (Section section : sections) total += section.amount;
        return total;
    }

    @Override
    public void addInternalTriggers(Collection<Trigger.Internal> triggers) {
        triggers.add(BCTransportStatements.TRIGGER_FLUIDS_TRAVERSING);
    }

    @Override
    public boolean canConnect(Direction face, PipeFlow other) {
        return other instanceof FluidFlow;
    }

    @Override
    public boolean canConnectTile(Direction face, BlockEntity tile) {
        return Ports.fluids(pipe.getLevel(), tile.getBlockPos(), face.getOpposite()) != null;
    }

    // Extraction (wooden pipes).

    /** @return the fluid pulled from the tank on {@code from}, in mB. */
    public FluidAmount tryExtractFluid(int millibuckets, Direction from, boolean simulate) {
        if (from == null || millibuckets <= 0) return FluidAmount.EMPTY;
        FluidPort port = Ports.fluids(pipe.getLevel(), pipe.getBlockPos().relative(from), from.getOpposite());
        if (port == null) return FluidAmount.EMPTY;
        Section section = section(from);
        Section middle = section(null);
        millibuckets = Math.min(millibuckets, capacity * 2 - section.amount - middle.amount);
        if (millibuckets <= 0) return FluidAmount.EMPTY;
        FluidAmount toAdd = port.drain(currentFluid == Fluids.EMPTY ? null : currentFluid, millibuckets, simulate);
        if (toAdd.isEmpty()) return FluidAmount.EMPTY;
        Fluid fluid = Fluids2.still(toAdd.fluid());
        if (currentFluid != Fluids.EMPTY && fluid != currentFluid) return FluidAmount.EMPTY;
        int amount = (int) toAdd.amount();
        if (currentFluid == Fluids.EMPTY && !simulate) setFluid(fluid);
        int filled = section.fillInternal(amount, !simulate);
        middle.fillInternal(amount - filled, !simulate);
        if (!simulate) {
            section.ticksInDirection = COOLDOWN_INPUT;
            pipe.setChanged();
        }
        return new FluidAmount(fluid, amount);
    }

    // Ticking.

    @Override
    public void serverTick() {
        if (currentFluid != Fluids.EMPTY) {
            int totalFluid = 0;
            boolean canOutput = false;
            for (Section section : sections) {
                section.currentTime = (section.currentTime + 1) % transferDelay;
                section.advanceForMovement();
                totalFluid += section.amount;
                if (section.direction().canOutput()) canOutput = true;
            }
            if (totalFluid == 0) {
                setFluid(Fluids.EMPTY);
            } else {
                if (canOutput) moveFromPipe();
                moveFromCenter();
                moveToCenter();
            }
            for (Section section : sections) {
                if (section.ticksInDirection > 0) section.ticksInDirection--;
                else if (section.ticksInDirection < 0) section.ticksInDirection++;
            }
            pipe.setChanged();
        }
        boolean send = false;
        for (Section section : sections) {
            if (section.amount != section.lastSentAmount || section.lastSentDirection != section.direction()) {
                send = true;
                break;
            }
        }
        long now = pipe.getLevel().getGameTime();
        if (send && now - lastSyncTime >= NETWORK_UPDATE_RATE) {
            lastSyncTime = now;
            pipe.queueSync();
        }
    }

    private void moveFromPipe() {
        for (Direction face : Direction.values()) {
            Section section = section(face);
            if (!section.direction().canOutput()) continue;
            int maxDrain = section.drainInternal(transferPerTick, false);
            if (maxDrain <= 0) continue;
            SideCheck sideCheck = new SideCheck(ItemStack.EMPTY, null);
            sideCheck.disallowAllExcept(face);
            pipe.behaviour().fluidSideCheck(sideCheck);
            if (sideCheck.firstOrder().size() != 1) continue;
            FluidPort target = neighbourPort(face);
            if (target == null) continue;
            long filled = target.fill(currentFluid, maxDrain, false);
            if (filled > 0) {
                section.drainInternal((int) filled, true);
                section.ticksInDirection = COOLDOWN_OUTPUT;
            }
        }
    }

    @Nullable
    private FluidPort neighbourPort(Direction face) {
        if (!pipe.isConnected(face)) return null;
        return Ports.fluids(pipe.getLevel(), pipe.getBlockPos().relative(face), face.getOpposite());
    }

    private void moveFromCenter() {
        Section center = section(null);
        int totalAvailable = center.maxDrained();
        if (totalAvailable < 1) return;
        EnumSet<Direction> realDirections = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.values()) {
            Section section = section(direction);
            if (!section.direction().canOutput()) continue;
            if (section.maxFilled() > 0 && neighbourPort(direction) != null) realDirections.add(direction);
        }
        if (realDirections.isEmpty()) return;
        SideCheck sideCheck = new SideCheck(ItemStack.EMPTY, null);
        sideCheck.disallowAllExcept(realDirections);
        pipe.behaviour().fluidSideCheck(sideCheck);
        List<Direction> random = new ArrayList<>(sideCheck.firstOrder());
        Collections.shuffle(random);
        float min = Math.min(transferPerTick * realDirections.size(), totalAvailable) / (float) transferPerTick / realDirections.size();
        for (Direction direction : random) {
            Section section = section(direction);
            int available = section.fill(transferPerTick, false);
            int amountToPush = (int) (available * min);
            if (amountToPush < 1) amountToPush++;
            amountToPush = center.drainInternal(amountToPush, false);
            if (amountToPush > 0) {
                int filled = section.fill(amountToPush, true);
                if (filled > 0) {
                    center.drainInternal(filled, true);
                    section.ticksInDirection = COOLDOWN_OUTPUT;
                }
            }
        }
    }

    private void moveToCenter() {
        int transferInCount = 0;
        Section center = section(null);
        int spaceAvailable = capacity - center.amount;
        if (spaceAvailable <= 0 || center.maxFilled() <= 0) return;
        List<Direction> faces = new ArrayList<>(List.of(Direction.values()));
        Collections.shuffle(faces);
        int[] inputPerTick = new int[6];
        for (Direction face : faces) {
            Section section = section(face);
            int i = face.get3DDataValue();
            if (section.direction().canInput()) {
                inputPerTick[i] = section.drainInternal(transferPerTick, false);
                if (inputPerTick[i] > 0) transferInCount++;
            }
        }
        int[] fluidLeavingSide = new int[6];
        int left = Math.min(transferPerTick, spaceAvailable);
        float min = Math.min(transferPerTick * transferInCount, spaceAvailable) / (float) transferPerTick / transferInCount;
        for (Direction face : Direction.values()) {
            int i = face.get3DDataValue();
            if (inputPerTick[i] <= 0) continue;
            int amountToDrain = (int) (inputPerTick[i] * min);
            if (amountToDrain < 1) amountToDrain++;
            if (amountToDrain > left) amountToDrain = left;
            int amountToPush = section(face).drainInternal(amountToDrain, false);
            if (amountToPush > 0) {
                fluidLeavingSide[i] = amountToPush;
                left -= amountToPush;
            }
        }
        int[] fluidEnteringCentre = Arrays.copyOf(fluidLeavingSide, 6);
        pipe.behaviour().onMoveFluidToCentre(fluidEnteringCentre);
        for (Direction face : Direction.values()) {
            int i = face.get3DDataValue();
            int leaving = fluidLeavingSide[i];
            if (leaving <= 0) continue;
            Section section = section(face);
            int drained = section.drainInternal(leaving, true);
            if (drained > 0) section.ticksInDirection = COOLDOWN_INPUT;
            if (fluidEnteringCentre[i] > 0) center.fill(fluidEnteringCentre[i], true);
        }
    }

    @Override
    public void addDrops(List<ItemStack> drops) {
        if (currentFluid != Fluids.EMPTY) FragileFluidShardItem.addFluidDrops(drops, new FluidAmount(currentFluid, totalAmount()));
    }

    @Nullable
    @Override
    public FluidPort fluidPort(Direction side) {
        Section section = section(side);
        return new FluidPort() {
            @Override
            public long fill(Fluid fluid, long amount, boolean simulate) {
                return section.fillExternal(Fluids2.still(fluid), (int) Math.min(Integer.MAX_VALUE, amount), simulate);
            }

            @Override
            public FluidAmount drain(@Nullable Fluid filter, long amount, boolean simulate) {
                return FluidAmount.EMPTY;
            }
        };
    }

    // Persistence.

    @Override
    public void save(CompoundTag tag) {
        if (currentFluid == Fluids.EMPTY) return;
        tag.putString("fluid", Registry.FLUID.getKey(currentFluid).toString());
        for (Section section : sections) {
            CompoundTag sub = new CompoundTag();
            sub.putShort("capacity", (short) section.amount);
            sub.putShort("ticksInDirection", (short) section.ticksInDirection);
            for (int i = 0; i < section.incoming.length; i++) sub.putShort("in[" + i + "]", (short) section.incoming[i]);
            tag.put("tank[" + section.index() + "]", sub);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("fluid"));
        Fluid fluid = id == null ? Fluids.EMPTY : Registry.FLUID.get(id);
        setFluid(fluid);
        for (Section section : sections) section.amount = 0;
        if (fluid == Fluids.EMPTY) return;
        for (Section section : sections) {
            CompoundTag sub = tag.getCompound("tank[" + section.index() + "]");
            section.amount = sub.getShort("capacity");
            section.ticksInDirection = sub.getShort("ticksInDirection");
            section.incomingTotalCache = 0;
            for (int i = 0; i < section.incoming.length; i++) {
                section.incomingTotalCache += section.incoming[i] = sub.getShort("in[" + i + "]");
            }
        }
    }

    // Client sync and rendering.

    @Override
    public void writeClient(CompoundTag tag) {
        tag.putString("fluid", Registry.FLUID.getKey(currentFluid).toString());
        int[] amounts = new int[7];
        int[] directions = new int[7];
        for (Section section : sections) {
            amounts[section.index()] = section.amount;
            directions[section.index()] = section.direction().ordinal();
            section.lastSentAmount = section.amount;
            section.lastSentDirection = section.direction();
        }
        tag.putIntArray("amounts", amounts);
        tag.putIntArray("directions", directions);
    }

    @Override
    public void readClient(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("fluid"));
        Fluid fluid = id == null ? Fluids.EMPTY : Registry.FLUID.get(id);
        if (fluid != Fluids.EMPTY) clientFluid = fluid;
        int[] amounts = tag.getIntArray("amounts");
        int[] directions = tag.getIntArray("directions");
        if (amounts.length != 7 || directions.length != 7) return;
        boolean first = lastMessage == 0;
        for (Section section : sections) {
            section.target = amounts[section.index()];
            if (first) section.clientAmountLast = section.clientAmountThis = section.target;
            Dir dir = Dir.values()[Math.floorMod(directions[section.index()], 3)];
            section.ticksInDirection = dir == Dir.NONE ? 0 : dir == Dir.IN ? COOLDOWN_INPUT : COOLDOWN_OUTPUT;
        }
        lastMessageMinus1 = lastMessage;
        lastMessage = pipe.hasLevel() ? pipe.getLevel().getGameTime() : 1;
    }

    @Override
    public void clientTick() {
        for (Section section : sections) section.tickClient();
    }

    public Fluid fluidForRender() {
        return clientFluid;
    }

    /** Upstream {@code getAmountsForRender}: interpolated amounts indexed by face ordinal, centre last. */
    public double[] amountsForRender(float partialTicks) {
        double[] amounts = new double[7];
        for (Section section : sections) {
            amounts[section.index()] = section.clientAmountLast * (1 - partialTicks) + section.clientAmountThis * partialTicks;
        }
        return amounts;
    }

    enum Dir {
        NONE, IN, OUT;

        boolean canInput() {
            return this != OUT;
        }

        boolean canOutput() {
            return this != IN;
        }
    }

    private final class Section {
        @Nullable
        final Direction face;
        int amount;
        int lastSentAmount;
        Dir lastSentDirection = Dir.NONE;
        int currentTime;
        int[] incoming = new int[1];
        int incomingTotalCache;
        int ticksInDirection;
        int clientAmountThis;
        int clientAmountLast;
        int target;

        Section(@Nullable Direction face) {
            this.face = face;
        }

        int index() {
            return face == null ? CENTER : face.get3DDataValue();
        }

        int maxFilled() {
            return Math.min(capacity - amount, transferPerTick - incoming[currentTime]);
        }

        int maxDrained() {
            return Math.min(amount - incomingTotalCache, transferPerTick);
        }

        int fill(int maxFill, boolean doFill) {
            int toFill = Math.min(maxFilled(), maxFill);
            if (toFill <= 0) return 0;
            if (doFill) {
                incoming[currentTime] += toFill;
                incomingTotalCache += toFill;
                amount += toFill;
            }
            return toFill;
        }

        int fillInternal(int maxFill, boolean doFill) {
            int toFill = Math.min(capacity - amount, maxFill);
            if (toFill <= 0) return 0;
            if (doFill) {
                incoming[currentTime] += toFill;
                incomingTotalCache += toFill;
                amount += toFill;
            }
            return toFill;
        }

        int drainInternal(int maxDrain, boolean doDrain) {
            maxDrain = Math.min(maxDrain, maxDrained());
            if (maxDrain <= 0) return 0;
            if (doDrain) amount -= maxDrain;
            return maxDrain;
        }

        void advanceForMovement() {
            incomingTotalCache -= incoming[currentTime];
            incoming[currentTime] = 0;
        }

        Dir direction() {
            return ticksInDirection == 0 ? Dir.NONE : ticksInDirection < 0 ? Dir.IN : Dir.OUT;
        }

        /** Upstream {@code Section.fill(FluidStack, boolean)}, used by neighbouring blocks. */
        int fillExternal(Fluid fluid, int amountIn, boolean simulate) {
            if (face == null || fluid == Fluids.EMPTY || amountIn <= 0) return 0;
            if (!direction().canInput() || !pipe.isConnected(face) || !pipe.behaviour().canInsertFluid(face)) return 0;
            if (currentFluid != Fluids.EMPTY && currentFluid != fluid) return 0;
            if (!simulate && currentFluid == Fluids.EMPTY) setFluid(fluid);
            int filled = fill(amountIn, !simulate);
            if (filled > 0 && !simulate) {
                ticksInDirection = COOLDOWN_INPUT;
                pipe.setChanged();
            }
            return filled;
        }

        void tickClient() {
            clientAmountLast = clientAmountThis;
            if (target != clientAmountThis) {
                int delta = target - clientAmountThis;
                int msgDelta = (int) Math.max(1, Math.min(60, lastMessage - lastMessageMinus1));
                if (Math.abs(delta) < msgDelta) clientAmountThis += delta;
                else clientAmountThis += delta / msgDelta;
            }
        }
    }
}
