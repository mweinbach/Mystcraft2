package com.technovision.advancedgenetics.api.blockentity;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class AbstractProcessingBlockEntity extends BlockEntity implements ProcessingBlockEntity, MenuProvider, Nameable {

    public static final int DATA_COUNT = 5;

    private final Component name;
    private int progress = 0;
    private int overclock = 0;
    private final int maxOverclock;
    private final MachineEnergyStorage energyStorage;
    private final int maxProgress;
    private final ContainerData propertyDelegate;
    private LazyOptional<IEnergyStorage> energyCapability;

    public AbstractProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int maxProgress, int maxOverclock) {
        super(type, pos, state);
        String blockEntityName = Objects.requireNonNull(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(type)).getPath();
        this.name = Component.translatable(String.format("%s.container.%s", AdvancedGenetics.MOD_ID, blockEntityName));
        this.energyStorage = new MachineEnergyStorage(energyCapacity);
        this.energyCapability = LazyOptional.of(() -> energyStorage);
        this.maxOverclock = maxOverclock;
        this.maxProgress = maxProgress;
        this.propertyDelegate = new ContainerData() {
            public int get(int index) {
                return switch (index) {
                    case 0 -> getProgress();
                    case 1 -> getMaxProgress();
                    case 2 -> getEnergyStorage().getEnergyStored();
                    case 3 -> getEnergyStorage().getMaxEnergyStored();
                    case 4 -> getOverclock();
                    default -> 0;
                };
            }
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> setProgress(value);
                    case 2 -> energyStorage.setEnergy(value);
                    case 4 -> setOverclock(value);
                }
            }
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    @Override
    public Component getName() {
        return name;
    }

    @Override
    public Component getDisplayName() {
        return getName();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide()) {
            updateRecipe();
            if (canProcessRecipe()) {
                processRecipe();
            } else if (progress > 0) {
                setProgress(0);
                setRecipe(null);
            }
        }
    }

    @Override
    public int getProgress() {
        return progress;
    }

    @Override
    public void setProgress(int progress) {
        this.progress = progress;
    }

    @Override
    public void incrementProgress() {
        this.progress++;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        nbt.putInt("progress", progress);
        nbt.putLong("energy", energyStorage.getEnergyStored());
        nbt.putInt("overclock", overclock);
        super.saveAdditional(nbt);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        setProgress(nbt.getInt("progress"));
        energyStorage.setEnergy((int) Math.min(nbt.getLong("energy"), energyStorage.getMaxEnergyStored()));
        int oc = nbt.getInt("overclock");
        if (oc > maxOverclock) oc = maxOverclock;
        setOverclock(oc);
    }

    public IEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    /**
     * Inserts energy only if the full amount fits.
     */
    public void insertEnergy(int value) {
        if (energyStorage.receiveEnergy(value, true) == value) {
            energyStorage.receiveEnergy(value, false);
        }
    }

    /**
     * Extracts energy only if the full amount is available.
     */
    public void extractEnergy(int value) {
        if (energyStorage.extractEnergy(value, true) == value) {
            energyStorage.extractEnergy(value, false);
        }
    }

    public void forceSync() {
        this.setChanged();
        if (level != null) {
            level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public boolean canOverclock() {
        return overclock < maxOverclock && getMaxProgress() >= 20;
    }

    public void setOverclock(int overclock) {
        this.overclock = overclock;
    }

    public void incrementOverclock() {
        this.overclock++;
        setChanged();
    }

    public void decrementOverclock() {
        this.overclock--;
        setChanged();
    }

    public int getOverclock() {
        return overclock;
    }

    public int getMaxProgress() {
        int base = 20 * Config.Common.overclockSpeed.get();
        int realMaxProgress = (maxProgress - (base * getOverclock()));
        if (realMaxProgress < 20) realMaxProgress = 20;
        return realMaxProgress;
    }

    public ContainerData getPropertyDelegate() {
        return propertyDelegate;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCapability = LazyOptional.of(() -> energyStorage);
    }

    /**
     * Forge energy buffer matching upstream's SimpleEnergyStorage(capacity, capacity, capacity).
     */
    private class MachineEnergyStorage extends EnergyStorage {

        MachineEnergyStorage(int capacity) {
            super(capacity, capacity, capacity);
        }

        void setEnergy(int energy) {
            this.energy = Math.max(0, Math.min(energy, capacity));
            setChanged();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) setChanged();
            return received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            int extracted = super.extractEnergy(maxExtract, simulate);
            if (extracted > 0 && !simulate) setChanged();
            return extracted;
        }
    }
}
