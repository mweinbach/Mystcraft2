/*
 * Extreme Reactors 1.19.2 port - Fabric adapter tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.client.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The provider picks a block state's original model from its blockstate file the way vanilla matches variant keys.
 */
class BlockStateModelsTest {

    private static final Map<String, String> STATE = Map.of("axis", "x", "assembled", "true");

    @Test
    void anEmptyKeyMatchesEveryState() {
        assertTrue(BlockStateModels.matches("", STATE));
    }

    @Test
    void everyConditionMustMatchInAnyOrder() {

        assertTrue(BlockStateModels.matches("axis=x", STATE));
        assertTrue(BlockStateModels.matches("axis=x,assembled=true", STATE));
        assertTrue(BlockStateModels.matches("assembled=true,axis=x", STATE));
    }

    @Test
    void aWrongValueOrUnknownPropertyDoesNotMatch() {

        assertFalse(BlockStateModels.matches("axis=y", STATE));
        assertFalse(BlockStateModels.matches("axis=x,assembled=false", STATE));
        assertFalse(BlockStateModels.matches("facing=north", STATE));
        assertFalse(BlockStateModels.matches("axis", STATE));
    }
}
