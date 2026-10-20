package ports.buildcraft.silicon;

/** Upstream {@code ILaserTarget}: a block entity that lasers power, in micro-MJ. */
public interface LaserTarget {
    /** How much more power this target wants; zero when it needs none. */
    long getRequiredLaserPower();

    /** Takes up to the power it needs and returns the excess. */
    long receiveLaserPower(long microJoules);
}
