/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.result;

import ports.extremereactors.corelib.data.stack.OperationMode;

/**
 * A place a machine stores recipe results in, such as an output slot or a tank.
 *
 * @param <R> the result type
 */
public interface IRecipeResultTarget<R extends IRecipeResult<?>> {

    /** How many complete copies of the result the target can still accept. */
    int countStorableResults(R result);

    /**
     * Store one copy of the result.
     *
     * @return the amount that was (or, when simulating, would be) stored
     */
    long setResult(R result, OperationMode mode);
}
