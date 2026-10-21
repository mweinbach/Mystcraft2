/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.registries;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired on the mod bus once per registry, in the loader's registration order, to add entries to it.
 */
public class RegisterEvent extends Event {

    @FunctionalInterface
    public interface Registrar {

        void register(ResourceLocation name, Object value);
    }

    @FunctionalInterface
    public interface RegisterHelper<T> {

        void register(ResourceLocation name, T value);

        default void register(String name, T value) {
            register(new ResourceLocation(name), value);
        }

        default void register(ResourceKey<T> key, T value) {
            register(key.location(), value);
        }
    }

    private final ResourceKey<? extends Registry<?>> registryKey;
    private final Registrar registrar;

    public RegisterEvent(ResourceKey<? extends Registry<?>> registryKey, Registrar registrar) {
        this.registryKey = registryKey;
        this.registrar = registrar;
    }

    public ResourceKey<? extends Registry<?>> getRegistryKey() {
        return registryKey;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> IForgeRegistry<T> getForgeRegistry() {
        return (IForgeRegistry<T>) RegistryManager.ACTIVE.getRegistry((ResourceKey) registryKey);
    }

    public <T> void register(ResourceKey<? extends Registry<T>> key, ResourceLocation name, Supplier<T> valueSupplier) {
        if (registryKey.equals(key)) {
            registrar.register(name, valueSupplier.get());
        }
    }

    @SuppressWarnings("unchecked")
    public <T> void register(ResourceKey<? extends Registry<T>> key, Consumer<RegisterHelper<T>> consumer) {
        if (registryKey.equals(key)) {
            consumer.accept((name, value) -> registrar.register(name, value));
        }
    }
}
