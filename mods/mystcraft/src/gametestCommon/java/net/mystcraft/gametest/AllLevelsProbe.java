package net.mystcraft.gametest;

import java.util.concurrent.atomic.AtomicInteger;

/** Counts calls seen by the test-only {@code getAllLevels} injection in {@code AllLevelsProbeMixin}. */
public final class AllLevelsProbe {
    private static final AtomicInteger CALLS = new AtomicInteger();

    private AllLevelsProbe() {}

    public static void record() { CALLS.incrementAndGet(); }
    public static int calls() { return CALLS.get(); }
}
