package ports.gridwright.power;

import ports.gridwright.machine.GeneratorKind;

/**
 * Overclock upgrades for generators. Each installed upgrade adds the base rate again, so a fuel burns
 * faster, but costs a tenth of the fuel's total: speed is bought with efficiency, never made from
 * nothing. With {@link #MAX} upgrades a fuel burns five times as fast for 60% of its energy.
 */
public final class Overclock {

    public static final int MAX = 4;

    private Overclock() { }

    /** The fuel as burnt by a generator with {@code upgrades} overclock upgrades installed. */
    public static GeneratorKind.Fuel apply(GeneratorKind.Fuel fuel, int upgrades) {
        int n = Math.max(0, Math.min(MAX, upgrades));
        if (n == 0) return fuel;
        long rate = (long) fuel.perTick() * (1 + n);
        long total = fuel.total() * (10 - n) / 10;
        int ticks = (int) Math.max(1, total / rate);
        return new GeneratorKind.Fuel((int) Math.min(Integer.MAX_VALUE, rate), ticks, fuel.remainder());
    }
}
