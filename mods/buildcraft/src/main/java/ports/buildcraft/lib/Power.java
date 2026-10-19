package ports.buildcraft.lib;

/**
 * BuildCraft keeps upstream's micro-Minecraft-Joule bookkeeping internally so every constant, rate
 * and heat formula matches 8.0.x exactly. Power crosses block boundaries only through the loader
 * energy APIs (Forge Energy, Team Reborn Energy) at one fixed ratio:
 *
 * <p>1 MJ = {@value #UNITS_PER_MJ} energy units (FE on Forge, E on Fabric).
 *
 * <p>This is upstream's own default ({@code MjRfConversion.DEFAULT_MJ_PER_RF = MjAPI.MJ / 10}) and the
 * classic BuildCraft 6/7 rate that Tekxit-era packs balanced around. See PARITY.md for the derivation.
 */
public final class Power {
    /** One Minecraft Joule in micro-Joules, as {@code MjAPI.MJ}. */
    public static final long MJ = 1_000_000L;
    public static final long UNITS_PER_MJ = 10;
    /** The micro-Joules carried by one loader energy unit. */
    public static final long MICRO_MJ_PER_UNIT = MJ / UNITS_PER_MJ;

    private Power() {}

    /** Whole energy units available in {@code microJoules}; remainders stay with the caller. */
    public static long toUnits(long microJoules) {
        return microJoules <= 0 ? 0 : microJoules / MICRO_MJ_PER_UNIT;
    }

    public static long fromUnits(long units) {
        if (units <= 0) return 0;
        return units > Long.MAX_VALUE / MICRO_MJ_PER_UNIT ? Long.MAX_VALUE : units * MICRO_MJ_PER_UNIT;
    }

    public static String formatMj(long microJoules) {
        double mj = microJoules / (double) MJ;
        return mj == Math.rint(mj) ? String.format("%.0f", mj) : String.format("%.2f", mj);
    }
}
