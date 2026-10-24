/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.util;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Pairs each input with a different test it passes, as a shapeless recipe pairs stacks with ingredients.
 */
public final class RecipeMatcher {

    private RecipeMatcher() {
    }

    /**
     * @return {@code null} if there is no complete pairing, otherwise an array where entry {@code t} is the index of
     * the input paired with test {@code t}
     */
    @Nullable
    public static <T> int[] findMatches(List<T> inputs, List<? extends Predicate<T>> tests) {
        int size = inputs.size();
        if (size != tests.size()) {
            return null;
        }
        boolean[][] passes = new boolean[size][size];
        for (int input = 0; input < size; input++) {
            for (int test = 0; test < size; test++) {
                passes[input][test] = tests.get(test).test(inputs.get(input));
            }
        }
        int[] inputForTest = new int[size];
        Arrays.fill(inputForTest, -1);
        for (int input = 0; input < size; input++) {
            if (!assign(input, passes, inputForTest, new boolean[size])) {
                return null;
            }
        }
        return inputForTest;
    }

    private static boolean assign(int input, boolean[][] passes, int[] inputForTest, boolean[] visited) {
        for (int test = 0; test < inputForTest.length; test++) {
            if (passes[input][test] && !visited[test]) {
                visited[test] = true;
                if (inputForTest[test] == -1 || assign(inputForTest[test], passes, inputForTest, visited)) {
                    inputForTest[test] = input;
                    return true;
                }
            }
        }
        return false;
    }
}
