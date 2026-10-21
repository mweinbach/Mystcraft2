package ports.buildcraft.factory;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.buildcraft.lib.HasWork;
import ports.buildcraft.lib.MjBattery;
import ports.buildcraft.lib.Power;
import ports.buildcraft.lib.SyncedBlockEntity;
import ports.buildcraft.platform.EnergyPort;
import ports.buildcraft.platform.PortHolder;

/** Upstream {@code TileMiner}: a powered block that lowers a tube straight down. */
public abstract class MinerBlockEntity extends SyncedBlockEntity implements PortHolder, HasWork {
    /** Upstream {@code BCCoreConfig.miningMaxDepth} default. */
    public static final int MAX_DEPTH = 512;

    protected long progress;
    @Nullable protected BlockPos currentPos;
    protected final MjBattery battery = new MjBattery(getBatteryCapacity());
    private final EnergyPort energyPort = battery.receiver(acceptsRedstoneEngine(), this::setChanged);
    private int wantedLength;
    private boolean clientComplete;
    private double currentLength;
    private double lastLength;
    private int offset = -1;

    protected MinerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    protected abstract void mine();

    protected long getBatteryCapacity() {
        return 500 * Power.MJ;
    }

    protected boolean acceptsRedstoneEngine() {
        return false;
    }

    void serverTick() {
        if (offset < 0 && level != null) offset = level.random.nextInt(10);
        battery.tick();
        if (level != null && level.getGameTime() % 10 == offset) queueSync();
        mine();
        flushSync();
    }

    void clientTick() {
        lastLength = currentLength;
        if (Math.abs(wantedLength - currentLength) <= 0.01) currentLength = wantedLength;
        else currentLength = currentLength + (wantedLength - currentLength) / 7D;
    }

    /** Removes this miner's tube blocks when it is broken. */
    void onRemoved() {
        if (level == null) return;
        for (int y = worldPosition.getY() - 1; y > worldPosition.getY() - MAX_DEPTH && y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(worldPosition.getX(), y, worldPosition.getZ());
            if (!level.getBlockState(pos).is(BCFactory.TUBE.get())) break;
            level.removeBlock(pos, false);
        }
    }

    protected void updateLength() {
        if (level == null) return;
        BlockPos target = getTargetPos();
        int newY = target != null ? target.getY() : worldPosition.getY();
        int newLength = worldPosition.getY() - newY;
        if (newLength != wantedLength) {
            onRemoved();
            for (int y = worldPosition.getY() - 1; y > newY; y--) {
                level.setBlock(new BlockPos(worldPosition.getX(), y, worldPosition.getZ()), BCFactory.TUBE.get().defaultBlockState(), 3);
            }
            currentLength = wantedLength = newLength;
            queueSync();
        }
    }

    @Nullable
    protected BlockPos getTargetPos() {
        return currentPos;
    }

    public double getLength(float partialTicks) {
        return lastLength * (1 - partialTicks) + currentLength * partialTicks;
    }

    public boolean isComplete() {
        return level != null && level.isClientSide ? clientComplete : currentPos == null;
    }

    /** Upstream read its client-only {@code isComplete} field here, so servers always reported work. */
    @Override
    public boolean hasWork() {
        return !isComplete();
    }

    public float getPercentFilledForRender() {
        float value = battery.stored() / (float) battery.capacity();
        return Math.max(0, Math.min(1, value));
    }

    public MjBattery battery() {
        return battery;
    }

    @Nullable
    @Override
    public EnergyPort energyPort(@Nullable Direction side) {
        return energyPort;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (currentPos != null) tag.put("currentPos", NbtUtils.writeBlockPos(currentPos));
        tag.putInt("wantedLength", wantedLength);
        tag.putLong("progress", progress);
        tag.put("battery", battery.save());
    }

    @Override
    protected void loadServer(CompoundTag tag) {
        currentPos = tag.contains("currentPos") ? NbtUtils.readBlockPos(tag.getCompound("currentPos")) : null;
        wantedLength = tag.getInt("wantedLength");
        progress = tag.getLong("progress");
        battery.load(tag.getCompound("battery"));
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putBoolean("complete", isComplete());
        tag.putLong("battery", battery.stored());
        tag.putInt("wantedLength", wantedLength);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        clientComplete = tag.getBoolean("complete");
        battery.set(tag.getLong("battery"));
        boolean first = wantedLength == 0 && currentLength == 0;
        wantedLength = tag.getInt("wantedLength");
        if (first) currentLength = lastLength = wantedLength;
    }
}
