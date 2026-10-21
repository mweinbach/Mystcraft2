/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model.data;

import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, loader-neutral data a block entity hands to its baked model. The loader adapters translate it into
 * Forge model data or a Fabric render attachment.
 */
public final class ModelData {

    public static final ModelData EMPTY = new ModelData(ImmutableMap.of());

    public static Builder builder() {
        return new Builder(null);
    }

    public Set<ModelProperty<?>> getProperties() {
        return this._properties.keySet();
    }

    public boolean has(final ModelProperty<?> property) {
        return this._properties.containsKey(property);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(final ModelProperty<T> property) {
        return (T) this._properties.get(property);
    }

    public Builder derive() {
        return new Builder(this);
    }

    public boolean isEmpty() {
        return this._properties.isEmpty();
    }

    @Override
    public String toString() {
        return "ModelData" + this._properties;
    }

    public static final class Builder {

        public <T> Builder with(final ModelProperty<T> property, @Nullable final T value) {

            if (!property.test(value)) {
                throw new IllegalArgumentException("Invalid value " + value + " for model property " + property);
            }

            if (null == value) {
                this._values.remove(property);
            } else {
                this._values.put(property, value);
            }

            return this;
        }

        public ModelData build() {
            return this._values.isEmpty() ? EMPTY : new ModelData(ImmutableMap.copyOf(this._values));
        }

        private Builder(@Nullable final ModelData parent) {

            this._values = new IdentityHashMap<>();

            if (null != parent) {
                this._values.putAll(parent._properties);
            }
        }

        private final Map<ModelProperty<?>, Object> _values;
    }

    @Override
    public boolean equals(final Object other) {
        return this == other || (other instanceof ModelData data && this._properties.equals(data._properties));
    }

    @Override
    public int hashCode() {
        return this._properties.hashCode();
    }

    private ModelData(final ImmutableMap<ModelProperty<?>, Object> properties) {
        this._properties = properties;
    }

    private final ImmutableMap<ModelProperty<?>, Object> _properties;
}
