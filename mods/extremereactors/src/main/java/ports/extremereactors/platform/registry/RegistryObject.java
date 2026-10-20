/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A handle to an object that is created and registered later, when the loader registers its registry.
 */
public final class RegistryObject<T>
        implements Supplier<T> {

    RegistryObject(final ResourceKey<? extends Registry<? super T>> registry, final ResourceLocation id,
                   final Supplier<? extends T> factory) {

        this._registry = registry;
        this._id = id;
        this._factory = factory;
    }

    public ResourceLocation getId() {
        return this._id;
    }

    public ResourceKey<? extends Registry<? super T>> getRegistryKey() {
        return this._registry;
    }

    public boolean isPresent() {
        return null != this._value;
    }

    @Override
    public T get() {
        return Objects.requireNonNull(this._value, () -> "Registry object " + this._id + " is not registered yet");
    }

    public Optional<T> asOptional() {
        return Optional.ofNullable(this._value);
    }

    /**
     * Create the object. Called by the loader backend exactly once, when the owning registry is populated.
     */
    public T create() {

        if (null != this._value) {
            throw new IllegalStateException("Registry object " + this._id + " was already created");
        }

        this._value = Objects.requireNonNull(this._factory.get(), () -> "Factory for " + this._id + " returned null");
        return this._value;
    }

    @Override
    public String toString() {
        return "RegistryObject{" + this._id + "}";
    }

    private final ResourceKey<? extends Registry<? super T>> _registry;
    private final ResourceLocation _id;
    private final Supplier<? extends T> _factory;
    private T _value;
}
