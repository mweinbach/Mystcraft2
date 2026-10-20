/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Filters and constraints for {@link TextInput}.
 */
public final class TextConstraints {

    /** Only accept digits. */
    public static final Predicate<Character> FILTER_NUMBERS = Character::isDigit;

    /**
     * A non negative integer: an empty text becomes 0, leading zeros are dropped and values past the integer range
     * are clamped to it.
     */
    public static final Function<String, Optional<String>> CONSTRAINT_POSITIVE_INTEGER_NUMBER = text -> {

        if (text.isEmpty()) {
            return Optional.of("0");
        }

        final long value;

        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException ex) {
            return Optional.of(text.chars().allMatch(Character::isDigit) ? Integer.toString(Integer.MAX_VALUE) : "0");
        }

        final String normalized = Long.toString(Math.max(0, Math.min(Integer.MAX_VALUE, value)));

        return normalized.equals(text) ? Optional.empty() : Optional.of(normalized);
    };

    /** A percentage: values above 100 become 100. */
    public static final Function<String, Optional<String>> CONSTRAINT_PERCENTAGE = text -> {

        try {
            return Long.parseLong(text) > 100 ? Optional.of("100") : Optional.empty();
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    };

    private TextConstraints() {
    }
}
