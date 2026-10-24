package ports.gridwright.block;

/**
 * The wards: blocks that stop one creature behaviour in the cube of {@link #radius()} blocks around
 * them. A ward with a {@link #gp()} cost works only while its owner's grid can pay for it.
 */
public enum WardKind {
    /** Hostile creatures do not spawn naturally nearby. */
    SPAWN("spawn_ward", Effect.SPAWNS, 16, 0),
    /** A larger spawn ward that runs on Grid Power. */
    GREATER_SPAWN("greater_spawn_ward", Effect.SPAWNS, 48, 8),
    /** Endermen cannot teleport out of, within or into the area. */
    ENDER("ender_ward", Effect.TELEPORTS, 16, 0);

    /** What a ward prevents. */
    public enum Effect { SPAWNS, TELEPORTS }

    private final String registryName;
    private final Effect effect;
    private final int radius;
    private final double gp;

    WardKind(String registryName, Effect effect, int radius, double gp) {
        this.registryName = registryName;
        this.effect = effect;
        this.radius = radius;
        this.gp = gp;
    }

    public String registryName() {
        return registryName;
    }

    public Effect effect() {
        return effect;
    }

    /** Half the side of the cube the ward covers, measured from the ward's centre. */
    public int radius() {
        return radius;
    }

    /** Grid Power the ward draws while it works; 0 for wards that need none. */
    public double gp() {
        return gp;
    }

    public boolean powered() {
        return gp > 0;
    }
}
