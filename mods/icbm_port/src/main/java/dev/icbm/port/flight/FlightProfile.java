package dev.icbm.port.flight;

/** Source defaults: ConfigMissile and LauncherCapability, ICBM Classic MIT code. */
public final class FlightProfile {
  public static final double DIRECT_SPEED = 2, SIMULATION_SPEED = 10, REENTRY_SPEED = 1;
  public static final int WARMUP_TICKS = 40, CRUISE_FUEL = 200, ARC_DISTANCE_LIMIT = 500;
  public static final int SIMULATION_EXIT_HEIGHT = 500, SIMULATION_ENTER_HEIGHT = 450;

  public static int health(int tier) {
    return switch (tier) {
      case 2 -> 80;
      case 3 -> 100;
      case 4 -> 200;
      default -> 50;
    };
  }

  public static int exitHeight(net.minecraft.world.World world) {
    return Math.min(SIMULATION_EXIT_HEIGHT, world.getTopY() - 2);
  }

  public static int enterHeight(net.minecraft.world.World world) {
    return Math.min(SIMULATION_ENTER_HEIGHT, world.getTopY() - 3);
  }

  private FlightProfile() {}
}
