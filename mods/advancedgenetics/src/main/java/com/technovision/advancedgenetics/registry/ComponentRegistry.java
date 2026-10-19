package com.technovision.advancedgenetics.registry;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge capability replacing the Cardinal Components player genetics component.
 */
public class ComponentRegistry {

    public static final ResourceLocation PLAYER_GENETICS_ID = new ResourceLocation(AdvancedGenetics.MOD_ID, "player_genetics");
    public static final Capability<PlayerGeneticsComponent> PLAYER_GENETICS = CapabilityManager.get(new CapabilityToken<>() {});

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerGeneticsComponent.class);
    }

    /**
     * Returns the player's genetics. Players always carry the capability; the detached
     * fallback only covers invalidated capabilities on a removed player instance.
     */
    public static PlayerGeneticsComponent getGenetics(Player player) {
        return player.getCapability(PLAYER_GENETICS).orElseGet(() -> new PlayerGeneticsComponent(player));
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {

        private final PlayerGeneticsComponent component;
        private final LazyOptional<PlayerGeneticsComponent> optional;

        public Provider(Player player) {
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

        public void invalidate() {
            optional.invalidate();
        }
    }
}
