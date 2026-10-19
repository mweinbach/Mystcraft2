package com.technovision.advancedgenetics.forge;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.blockentity.AbstractInventoryBlockEntity;
import com.technovision.advancedgenetics.api.blockentity.AbstractProcessingBlockEntity;
import com.technovision.advancedgenetics.api.blockentity.MachineEnergy;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.events.ComponentEvents;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge capabilities: player genetics (replacing the Cardinal Components player component), and
 * Forge Energy plus item handlers on the machines.
 */
public final class ForgeCapabilityEvents {

    public static final Capability<PlayerGeneticsComponent> PLAYER_GENETICS = CapabilityManager.get(new CapabilityToken<>() {});
    private static final ResourceLocation MACHINE_ID = new ResourceLocation(AdvancedGenetics.MOD_ID, "machine");

    private ForgeCapabilityEvents() { }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(PlayerGeneticsComponent.class));
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, ForgeCapabilityEvents::onAttachEntity);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, ForgeCapabilityEvents::onAttachBlockEntity);
        MinecraftForge.EVENT_BUS.addListener(ForgeCapabilityEvents::onPlayerClone);
        MinecraftForge.EVENT_BUS.addListener(ForgeCapabilityEvents::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> ComponentEvents.sync(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> ComponentEvents.sync(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> ComponentEvents.sync(event.getEntity()));
    }

    /**
     * Returns the player's genetics. Players always carry the capability; the detached
     * fallback only covers invalidated capabilities on a removed player instance.
     */
    static PlayerGeneticsComponent genetics(Player player) {
        return player.getCapability(PLAYER_GENETICS).orElseGet(() -> new PlayerGeneticsComponent(player));
    }

    private static void onAttachEntity(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) {
            GeneticsProvider provider = new GeneticsProvider(player);
            event.addCapability(ComponentRegistry.PLAYER_GENETICS_ID, provider);
            event.addListener(provider.optional::invalidate);
        }
    }

    private static void onAttachBlockEntity(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof AbstractProcessingBlockEntity machine) {
            MachineProvider provider = new MachineProvider(machine);
            event.addCapability(MACHINE_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    private static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            ComponentEvents.copyOnRespawn(original, event.getEntity(), event.isWasDeath());
        } finally {
            original.invalidateCaps();
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side.isServer()) {
            ComponentEvents.serverTick(event.player);
        }
    }

    private static final class GeneticsProvider implements ICapabilitySerializable<CompoundTag> {

        private final PlayerGeneticsComponent component;
        private final LazyOptional<PlayerGeneticsComponent> optional;

        GeneticsProvider(Player player) {
            this.component = new PlayerGeneticsComponent(player);
            this.optional = LazyOptional.of(() -> component);
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return PLAYER_GENETICS.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return component.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            component.deserializeNBT(tag);
        }
    }

    /**
     * Energy on every side, and item handlers: unsided access sees the whole inventory, sided access
     * follows the machine's face rules. Handlers are recreated after the block entity revives.
     */
    private static final class MachineProvider implements ICapabilityProvider {

        private final AbstractProcessingBlockEntity machine;
        private LazyOptional<IEnergyStorage> energy;
        private final LazyOptional<?>[] items = new LazyOptional<?>[Direction.values().length + 1];

        MachineProvider(AbstractProcessingBlockEntity machine) {
            this.machine = machine;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeCapabilities.ENERGY) {
                if (energy == null) {
                    energy = LazyOptional.of(() -> new EnergyView(machine.getEnergyStorage()));
                }
                return energy.cast();
            }
            if (cap == ForgeCapabilities.ITEM_HANDLER && machine instanceof AbstractInventoryBlockEntity inventory) {
                int index = side == null ? 0 : side.ordinal() + 1;
                if (items[index] == null) {
                    items[index] = side == null
                            ? LazyOptional.of(() -> (IItemHandler) new InvWrapper(inventory))
                            : LazyOptional.of(() -> (IItemHandler) new SidedInvWrapper(inventory, side));
                }
                return items[index].cast();
            }
            return LazyOptional.empty();
        }

        void invalidate() {
            if (energy != null) energy.invalidate();
            energy = null;
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) items[i].invalidate();
                items[i] = null;
            }
        }
    }

    /** Forge Energy view of a machine buffer, matching upstream's SimpleEnergyStorage(capacity, capacity, capacity). */
    private record EnergyView(MachineEnergy energy) implements IEnergyStorage {
        @Override public int receiveEnergy(int maxReceive, boolean simulate) { return energy.receiveEnergy(maxReceive, simulate); }
        @Override public int extractEnergy(int maxExtract, boolean simulate) { return energy.extractEnergy(maxExtract, simulate); }
        @Override public int getEnergyStored() { return energy.getEnergyStored(); }
        @Override public int getMaxEnergyStored() { return energy.getMaxEnergyStored(); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    }
}
