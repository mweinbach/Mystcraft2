package ports.buildcraft.transport.flow;

import java.util.ArrayList;
import java.util.List;

/** Upstream {@code DelayedList}: elements become due after a number of {@link #advance()} calls. */
final class DelayedList<E> {
    private final List<List<E>> elements = new ArrayList<>();

    int maxDelay() {
        return elements.size();
    }

    List<E> advance() {
        return elements.isEmpty() ? List.of() : elements.remove(0);
    }

    void add(int delay, E element) {
        if (delay < 0) delay = 0;
        while (elements.size() < delay + 1) elements.add(new ArrayList<>());
        elements.get(delay).add(element);
    }

    List<List<E>> all() {
        return elements;
    }

    void clear() {
        elements.clear();
    }
}
