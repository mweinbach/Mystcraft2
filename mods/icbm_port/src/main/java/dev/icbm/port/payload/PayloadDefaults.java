package dev.icbm.port.payload;

/**
 * Typed upstream defaults, with zero for quantities unused by that blast family. Forge
 * configuration files are intentionally not parsed by this Fabric implementation.
 *
 * <p>Antimatter and red matter deliberately depart from upstream: antimatter erases a much larger
 * sphere at once, and red matter's radius is the maximum radius of its spherical event horizon
 * rather than the upstream core size.
 */
public record PayloadDefaults(
    int radius, double entityDamage, int fragments, int durationTicks, double energy) {
  /** Port default antimatter radius (upstream ICBM Classic: 55). */
  public static final int ANTIMATTER_RADIUS = 275;

  /** Port default maximum red matter event-horizon radius (previous port default: 12). */
  public static final int REDMATTER_RADIUS = 300;

  /** Port default red matter lifetime, long enough for the horizon to reach full size. */
  public static final int REDMATTER_DURATION = 6000;

  /** Values written by earlier port versions; matching values are migrated on load. */
  static final int LEGACY_ANTIMATTER_RADIUS = 55,
      LEGACY_REDMATTER_RADIUS = 12,
      LEGACY_REDMATTER_DURATION = 2400;

  public static PayloadDefaults forType(PayloadType type) {
    return switch (type) {
      case CONDENSED -> new PayloadDefaults(6, 10, 0, 0, 6);
      case SHRAPNEL -> new PayloadDefaults(0, 11, 30, 0, 0);
      case ANVIL -> new PayloadDefaults(0, 30, 25, 0, 0);
      case FRAGMENTATION -> new PayloadDefaults(0, 11, 15, 0, 1.5);
      case INCENDIARY -> new PayloadDefaults(14, 0, 0, 0, 0);
      case DEBILITATION, CHEMICAL, CONTAGIOUS -> new PayloadDefaults(20, 0, 0, 600, 0);
      case REPULSIVE, ATTRACTIVE -> new PayloadDefaults(2, 0, 0, 0, 2);
      case SONIC -> new PayloadDefaults(15, 0, 0, 0, 0);
      case BREACHING -> new PayloadDefaults(1, 13, 0, 0, 15376);
      case THERMOBARIC -> new PayloadDefaults(30, 1000, 0, 0, 80);
      case NUCLEAR -> new PayloadDefaults(50, 1000, 0, 0, 80);
      case EMP -> new PayloadDefaults(50, 0, 0, 0, 0);
      case EXOTHERMIC, ENDOTHERMIC, ENDER -> new PayloadDefaults(30, 0, 0, 0, 0);
      case ANTI_GRAVITATIONAL -> new PayloadDefaults(30, 0, 0, 2400, 0);
      case ANTIMATTER -> new PayloadDefaults(ANTIMATTER_RADIUS, Integer.MAX_VALUE, 0, 0, 0);
      case REDMATTER -> new PayloadDefaults(REDMATTER_RADIUS, 2000, 0, REDMATTER_DURATION, 0);
      case ROT, MUTATION -> new PayloadDefaults(80, 0, 0, 0, 0);
      case COLOR -> new PayloadDefaults(10, 0, 0, 600, 0);
      case SMOKE -> new PayloadDefaults(0, 0, 0, 100, 0);
      case HYPERSONIC, MISSILEMODULE -> new PayloadDefaults(0, 0, 0, 0, 0);
    };
  }
}
