/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model.data;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable per-block data a block entity hands to its model. On Fabric it travels as the block entity's render
 * attachment.
 */
public final class ModelData {

    public static final ModelData EMPTY = builder().build();

    private final Map<ModelProperty<?>, Object> properties;

    private ModelData(Map<ModelProperty<?>, Object> properties) {
        this.properties = properties;
    }

    public Set<ModelProperty<?>> getProperties() {
        return properties.keySet();
    }

    public boolean has(ModelProperty<?> property) {
        return properties.containsKey(property);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(ModelProperty<T> property) {
        return (T) properties.get(property);
    }

    public Builder derive() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder(null);
    }

    public static final class Builder {

        private final Map<ModelProperty<?>, Object> properties = new IdentityHashMap<>();

        private Builder(@Nullable ModelData parent) {
            if (parent != null) {
                properties.putAll(parent.properties);
            }
        }

        public <T> Builder with(ModelProperty<T> property, T value) {
            Preconditions.checkState(property.test(value), "The provided value is invalid for this property.");
            properties.put(property, value);
            return this;
        }

        public ModelData build() {
            return new ModelData(Collections.unmodifiableMap(properties));
        }
    }
}
