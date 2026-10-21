/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.util;

@FunctionalInterface
public interface TriPredicate<A, B, C> {

    boolean test(A a, B b, C c);

    default TriPredicate<A, B, C> and(TriPredicate<? super A, ? super B, ? super C> other) {
        return (a, b, c) -> test(a, b, c) && other.test(a, b, c);
    }

    default TriPredicate<A, B, C> or(TriPredicate<? super A, ? super B, ? super C> other) {
        return (a, b, c) -> test(a, b, c) || other.test(a, b, c);
    }

    default TriPredicate<A, B, C> negate() {
        return (a, b, c) -> !test(a, b, c);
    }
}
