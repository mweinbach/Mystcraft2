/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.platform.Platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Collects objects for one registry. The loader backend creates and registers them when that registry is
 * populated (Forge: its {@code RegisterEvent}; Fabric: in vanilla registry order at the end of initialization).
 */
public final class DeferredRegister<T> {

    public static <T> DeferredRegister<T> create(final ResourceKey<? extends Registry<T>> registry, final String modId) {
        return new DeferredRegister<>(registry, modId);
    }

    public <I extends T> RegistryObject<I> register(final String name, final Supplier<? extends I> factory) {

        if (this._submitted) {
            throw new IllegalStateException("Cannot add " + name + " to a register that was already submitted");
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        final RegistryObject<I> entry = new RegistryObject<>((ResourceKey) this._registry, new ResourceLocation(this._modId, name), factory);

        this._entries.add(entry);
        return entry;
    }

    /**
     * Hand this register to the loader. Further {@link #register} calls are rejected.
     */
    public void register() {

        if (!this._submitted) {

            this._submitted = true;
            Platform.submitRegister(this);
        }
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return this._registry;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Collection<RegistryObject<T>> getEntries() {
        return (Collection) Collections.unmodifiableList(this._entries);
    }

    private DeferredRegister(final ResourceKey<? extends Registry<T>> registry, final String modId) {

        this._registry = registry;
        this._modId = modId;
        this._entries = new ArrayList<>();
    }

    private final ResourceKey<? extends Registry<T>> _registry;
    private final String _modId;
    private final List<RegistryObject<? extends T>> _entries;
    private boolean _submitted;
}
