package com.technovision.advancedgenetics.api.blockentity;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.Config;
import net.minecraft.core.BlockPos;
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

import java.util.Objects;

public abstract class AbstractProcessingBlockEntity extends BlockEntity implements ProcessingBlockEntity, MenuProvider, Nameable {

    public static final int DATA_COUNT = 5;

    private final Component name;
    private int progress = 0;
    private int overclock = 0;
    private final int maxOverclock;
    private final MachineEnergy energyStorage;
    private final int maxProgress;
    private final ContainerData propertyDelegate;

    public AbstractProcessingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int energyCapacity, int maxProgress, int maxOverclock) {
        super(type, pos, state);
        String blockEntityName = Objects.requireNonNull(BlockEntityType.getKey(type)).getPath();
        this.name = Component.translatable(String.format("%s.container.%s", AdvancedGenetics.MOD_ID, blockEntityName));
        this.energyStorage = new MachineEnergy(energyCapacity, this::setChanged);
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

    public MachineEnergy getEnergyStorage() {
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
}
