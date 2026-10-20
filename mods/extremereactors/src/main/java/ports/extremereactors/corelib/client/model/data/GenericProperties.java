/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model.data;

import java.util.Objects;

/**
 * Model properties shared by the variant-based block models: which block model to draw and which of its variants.
 */
public final class GenericProperties {

    /** The index of the block model (usually the ordinal of a part-type enum). */
    public static final NamedModelProperty<Integer> ID = new NamedModelProperty<>("ID", Objects::nonNull);
    /** The index of the variant of that block model. */
    public static final NamedModelProperty<Integer> VARIANT_INDEX = new NamedModelProperty<>("VARIANT_INDEX", Objects::nonNull);

    public static int getId(final ModelData data) {

        final Integer value = data.get(ID);

        return null != value ? value : 0;
    }

    public static int getVariantIndex(final ModelData data) {

        final Integer value = data.get(VARIANT_INDEX);

        return null != value ? value : 0;
    }

    private GenericProperties() {
    }
}
