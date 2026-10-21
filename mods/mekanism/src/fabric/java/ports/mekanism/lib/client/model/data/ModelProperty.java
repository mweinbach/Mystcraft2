/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model.data;

import com.google.common.base.Predicates;
import java.util.function.Predicate;

/**
 * A key into {@link ModelData}; compared by identity.
 */
public class ModelProperty<T> implements Predicate<T> {

    private final Predicate<T> predicate;

    public ModelProperty() {
        this(Predicates.alwaysTrue());
    }

    public ModelProperty(Predicate<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    public boolean test(T value) {
        return predicate.test(value);
    }
}
