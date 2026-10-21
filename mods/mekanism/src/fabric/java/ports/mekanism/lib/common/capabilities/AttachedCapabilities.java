/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.capabilities;

import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.util.INBTSerializable;
import ports.mekanism.lib.common.util.LazyOptional;

/**
 * The capabilities attached to one item stack, entity or block entity. Mixins give those classes a lazily created
 * instance; the providers come from {@link ports.mekanism.lib.event.AttachCapabilitiesEvent} or, for item stacks,
 * from {@code Item.initCapabilities}. Serializable providers are saved under {@link #TAG_KEY}, the key Forge uses, so
 * saved data reads the same on both loaders.
 */
public final class AttachedCapabilities {

    public static final String TAG_KEY = "ForgeCaps";

    private final Map<ResourceLocation, ICapabilityProvider> providers;
    private final List<Runnable> invalidationListeners;
    private boolean valid = true;

    public AttachedCapabilities(Map<ResourceLocation, ICapabilityProvider> providers, List<Runnable> invalidationListeners) {
        this.providers = Map.copyOf(providers);
        this.invalidationListeners = List.copyOf(invalidationListeners);
    }

    public static AttachedCapabilities of(@Nullable ICapabilityProvider provider) {
        return provider == null ? new AttachedCapabilities(Map.of(), List.of())
                                : new AttachedCapabilities(Map.of(new ResourceLocation("forge", "item"), provider), List.of());
    }

    @NotNull
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (!valid) {
            return LazyOptional.empty();
        }
        for (ICapabilityProvider provider : providers.values()) {
            LazyOptional<T> result = provider.getCapability(capability, side);
            if (result.isPresent()) {
                return result;
            }
        }
        return LazyOptional.empty();
    }

    public boolean isEmpty() {
        return providers.isEmpty();
    }

    public void invalidate() {
        valid = false;
        invalidationListeners.forEach(Runnable::run);
    }

    public void revive() {
        valid = true;
    }

    @Nullable
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        providers.forEach((key, provider) -> {
            if (provider instanceof INBTSerializable<?> serializable) {
                tag.put(key.toString(), serializable.serializeNBT());
            }
        });
        return tag.isEmpty() ? null : tag;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void deserialize(@Nullable CompoundTag tag) {
        if (tag == null) {
            return;
        }
        providers.forEach((key, provider) -> {
            if (provider instanceof INBTSerializable serializable && tag.contains(key.toString())) {
                Tag value = tag.get(key.toString());
                serializable.deserializeNBT(value);
            }
        });
    }

    /**
     * Implemented by mixins on the classes Forge makes capability providers. The capabilities are created on first use.
     */
    public interface Holder {

        AttachedCapabilities mekanism$capabilities();
    }
}
