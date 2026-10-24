/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.registries;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A reference to a registry entry by name, resolved once the entry is registered.
 */
public final class RegistryObject<T> implements Supplier<T> {

    private final ResourceLocation name;
    private final ResourceKey<? extends Registry<?>> registryKey;
    @Nullable
    private T value;

    private RegistryObject(ResourceLocation name, ResourceKey<? extends Registry<?>> registryKey) {
        this.name = name;
        this.registryKey = registryKey;
    }

    public static <T, U extends T> RegistryObject<U> create(ResourceLocation name, IForgeRegistry<T> registry) {
        return new RegistryObject<>(name, registry.getRegistryKey());
    }

    public static <T, U extends T> RegistryObject<U> create(ResourceLocation name, ResourceKey<? extends Registry<T>> registryKey, String modid) {
        return new RegistryObject<>(name, registryKey);
    }

    public static <T, U extends T> RegistryObject<U> create(ResourceLocation name, ResourceLocation registryName, String modid) {
        return new RegistryObject<>(name, ResourceKey.createRegistryKey(registryName));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private T resolve() {
        if (value == null) {
            IForgeRegistry<Object> registry = RegistryManager.ACTIVE.getRegistry((ResourceKey) registryKey);
            if (registry != null && registry.containsKey(name)) {
                value = (T) registry.getValue(name);
            }
        }
        return value;
    }

    @NotNull
    @Override
    public T get() {
        return Objects.requireNonNull(resolve(), () -> "Registry Object not present: " + name);
    }

    public ResourceLocation getId() {
        return name;
    }

    @SuppressWarnings("unchecked")
    public ResourceKey<T> getKey() {
        return ResourceKey.create((ResourceKey) registryKey, name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Holder<T>> getHolder() {
        IForgeRegistry<T> registry = RegistryManager.ACTIVE.getRegistry((ResourceKey) registryKey);
        return registry == null ? Optional.empty() : registry.getHolder(name);
    }

    public boolean isPresent() {
        return resolve() != null;
    }

    public void ifPresent(Consumer<? super T> consumer) {
        T resolved = resolve();
        if (resolved != null) {
            consumer.accept(resolved);
        }
    }

    public Optional<T> filter(Predicate<? super T> predicate) {
        T resolved = resolve();
        return resolved != null && predicate.test(resolved) ? Optional.of(resolved) : Optional.empty();
    }

    public <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        T resolved = resolve();
        return resolved == null ? Optional.empty() : Optional.ofNullable(mapper.apply(resolved));
    }

    public T orElse(T other) {
        T resolved = resolve();
        return resolved == null ? other : resolved;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof RegistryObject<?> other && name.equals(other.name) && registryKey.equals(other.registryKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, registryKey);
    }

    @Override
    public String toString() {
        return "RegistryObject[" + registryKey.location() + " / " + name + "]";
    }
}
