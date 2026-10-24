package ports.gridwright.grid;

/**
 * One grid's totals for a completed tick: capacity after diminishing returns, demand, and how many
 * producers and consumers reported.
 */
public record GridSnapshot(double capacity, double demand, int producers, int consumers) {

    public static final GridSnapshot EMPTY = new GridSnapshot(0, 0, 0, 0);

    /** Small enough to absorb floating-point noise, far below any real GP amount. */
    private static final double EPSILON = 1e-6;

    /** True while capacity covers demand; consumers on an overloaded grid do no work. */
    public boolean powered() {
        return demand <= capacity + EPSILON;
    }
}
