/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.world;

import com.mojang.serialization.Codec;

/**
 * A data-driven change to structures. Mekanism registers the serializer registry but no modifiers.
 */
public interface StructureModifier {

    Codec<? extends StructureModifier> codec();
}
