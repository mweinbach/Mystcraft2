/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.registries;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.eventbus.api.EventPriority;
import ports.mekanism.lib.eventbus.api.IEventBus;

/**
 * Entries to add to one registry when its {@link RegisterEvent} fires on the mod bus.
 */
public class DeferredRegister<T> {

    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String modid;
    private final Map<RegistryObject<T>, Supplier<? extends T>> entries = new LinkedHashMap<>();
    private final Set<RegistryObject<T>> entriesView = Collections.unmodifiableSet(entries.keySet());
    @Nullable
    private Supplier<RegistryBuilder<?>> registryFactory;
    @Nullable
    private IForgeRegistry<T> customRegistry;
    private boolean seenRegisterEvent;

    private DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String modid) {
        this.registryKey = Objects.requireNonNull(registryKey);
        this.modid = Objects.requireNonNull(modid);
    }

    public static <B> DeferredRegister<B> create(IForgeRegistry<B> registry, String modid) {
        return new DeferredRegister<>(registry.getRegistryKey(), modid);
    }

    public static <B> DeferredRegister<B> create(ResourceKey<? extends Registry<B>> key, String modid) {
        return new DeferredRegister<>(key, modid);
    }

    public static <B> DeferredRegister<B> create(ResourceLocation registryName, String modid) {
        return new DeferredRegister<>(ResourceKey.createRegistryKey(registryName), modid);
    }

    @SuppressWarnings("unchecked")
    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> sup) {
        if (seenRegisterEvent) {
            throw new IllegalStateException("Cannot register new entries to DeferredRegister after RegisterEvent has been fired.");
        }
        ResourceLocation key = new ResourceLocation(modid, name);
        RegistryObject<I> object = RegistryObject.create(key, (ResourceKey) registryKey, modid);
        if (entries.putIfAbsent((RegistryObject<T>) object, sup) != null) {
            throw new IllegalArgumentException("Duplicate registration " + name);
        }
        return object;
    }

    public Supplier<IForgeRegistry<T>> makeRegistry(Supplier<RegistryBuilder<T>> sup) {
        if (registryFactory != null) {
            throw new IllegalStateException("Cannot create a registry for a type that already exists");
        }
        registryFactory = (Supplier) sup;
        return () -> customRegistry != null ? customRegistry : RegistryManager.ACTIVE.getRegistry(registryKey);
    }

    @NotNull
    public TagKey<T> createTagKey(@NotNull String path) {
        return TagKey.create(registryKey, new ResourceLocation(modid, path));
    }

    @NotNull
    public TagKey<T> createTagKey(@NotNull ResourceLocation location) {
        return TagKey.create(registryKey, location);
    }

    public void register(IEventBus bus) {
        bus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, this::addEntries);
        if (registryFactory != null) {
            bus.addListener(EventPriority.NORMAL, false, NewRegistryEvent.class, this::createRegistry);
        }
    }

    @SuppressWarnings("unchecked")
    private void createRegistry(NewRegistryEvent event) {
        RegistryBuilder<T> builder = (RegistryBuilder<T>) registryFactory.get();
        builder.setName(registryKey.location());
        customRegistry = event.create(builder).get();
    }

    private void addEntries(RegisterEvent event) {
        if (event.getRegistryKey().equals(registryKey)) {
            seenRegisterEvent = true;
            for (Map.Entry<RegistryObject<T>, Supplier<? extends T>> entry : entries.entrySet()) {
                event.register(registryKey, entry.getKey().getId(), () -> entry.getValue().get());
            }
        }
    }

    public Collection<RegistryObject<T>> getEntries() {
        return entriesView;
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return registryKey;
    }

    public ResourceLocation getRegistryName() {
        return registryKey.location();
    }
}
