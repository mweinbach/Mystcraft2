/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * A tri-state test outcome: passed, failed, or skipped because there was nothing to test.
 */
public enum TestResult
        implements BooleanSupplier {

    PASSED,
    FAILED,
    SKIPPED;

    public static TestResult from(final boolean result) {
        return result ? PASSED : FAILED;
    }

    public static TestResult from(final Optional<Boolean> result) {
        return result.map(TestResult::from).orElse(SKIPPED);
    }

    public static <T> TestResult from(final Predicate<T> test, final T value) {
        return from(test.test(value));
    }

    public boolean wasSkipped() {
        return this == SKIPPED;
    }

    /**
     * @return true only if the test was run and passed
     */
    @Override
    public boolean getAsBoolean() {
        return this == PASSED;
    }
}
