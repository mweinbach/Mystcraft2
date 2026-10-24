package ports.gridwright.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;

/**
 * The Grid Power producers and how much each yields. The numbers are Gridwright's own: a panel gives
 * 4 GP in its half of the day under open sky (half in rain), a water mill 3 GP for every side with
 * flowing water beside it, and a wind mill up to 12 GP depending on height, open air and weather.
 */
public enum ProducerKind {
    SOLAR_PANEL,
    LUNAR_PANEL,
    WATER_MILL,
    WIND_MILL;

    public static final double PANEL_GP = 4.0;
    public static final double WATER_GP_PER_SIDE = 3.0;
    /** Wind mill output at sea level with every side open in clear weather; it grows 1 GP per 16 blocks up to 6. */
    public static final double WIND_BASE_GP = 1.0;
    public static final double WIND_HEIGHT_CAP = 6.0;

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** This producer's output at {@code pos} right now. */
    public double output(ServerLevel level, BlockPos pos) {
        boolean skylight = level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime();
        boolean sunUp = sunUp(level);
        return switch (this) {
            case SOLAR_PANEL -> solar(skylight && sunUp, level.canSeeSky(pos.above()), level.isRaining());
            case LUNAR_PANEL -> lunar(skylight && !sunUp, level.canSeeSky(pos.above()), level.isRaining());
            case WATER_MILL -> water(flowingSides(level, pos));
            case WIND_MILL -> wind(pos.getY(), level.getSeaLevel(), openSides(level, pos), level.isRaining(),
                    level.isThundering(), level.dimensionType().hasSkyLight());
        };
    }

    /**
     * Whether the sun is above the horizon, from the time of day alone. {@code Level.isDay()} is not
     * used because it reads the sky's darkness, which a thunderstorm deepens enough to call noon night;
     * weather instead only scales panel output through the rain multiplier.
     */
    public static boolean sunUp(ServerLevel level) {
        return sunUp(level.getTimeOfDay(1.0F));
    }

    /** {@code timeOfDay} is the celestial angle in [0, 1), where 0 is noon and 0.5 midnight. */
    public static boolean sunUp(float timeOfDay) {
        return Math.cos(timeOfDay * 2.0 * Math.PI) > 0;
    }

    public static double solar(boolean day, boolean seesSky, boolean raining) {
        return day && seesSky ? (raining ? PANEL_GP / 2 : PANEL_GP) : 0;
    }

    public static double lunar(boolean night, boolean seesSky, boolean raining) {
        return night && seesSky ? (raining ? PANEL_GP / 2 : PANEL_GP) : 0;
    }

    public static double water(int flowingSides) {
        return WATER_GP_PER_SIDE * Math.max(0, Math.min(4, flowingSides));
    }

    /**
     * {@code (1 + blocks above sea level / 16, capped at 6) * open sides / 8 * weather}, where rain
     * multiplies by 1.5 and thunder by 2. Nothing turns below sea level or where there is no sky.
     */
    public static double wind(int y, int seaLevel, int openSides, boolean raining, boolean thundering, boolean skylight) {
        if (!skylight || y < seaLevel) return 0;
        double height = Math.min(WIND_HEIGHT_CAP, WIND_BASE_GP + (y - seaLevel) / 16.0);
        double weather = thundering ? 2.0 : raining ? 1.5 : 1.0;
        return height * Math.max(0, Math.min(8, openSides)) / 8.0 * weather;
    }

    /** Horizontal neighbours holding flowing (non-source) water. */
    public static int flowingSides(ServerLevel level, BlockPos pos) {
        int sides = 0;
        for (Direction direction : HORIZONTAL) {
            FluidState fluid = level.getFluidState(pos.relative(direction));
            if (fluid.is(FluidTags.WATER) && !fluid.isSource()) sides++;
        }
        return sides;
    }

    /** Of the eight blocks around the mill at its own height, how many are air. */
    public static int openSides(ServerLevel level, BlockPos pos) {
        int open = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && level.isEmptyBlock(pos.offset(dx, 0, dz))) open++;
            }
        }
        return open;
    }
}
