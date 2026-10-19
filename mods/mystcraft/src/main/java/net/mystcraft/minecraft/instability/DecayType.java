package net.mystcraft.minecraft.instability;

/** The four complete spreading handlers in the pinned classic source. */
public enum DecayType {
    BLACK("black", .5F, 2.5F), RED("red", 1, 10), BLUE("blue", 5, 2), PURPLE("purple", 50, 100), WHITE("white", 50, 100);
    private final String id;
    private final float hardness, resistance;
    DecayType(String id, float hardness, float resistance) {
        this.id = id; this.hardness = hardness; this.resistance = resistance;
    }
    public String id() { return id; }
    public float hardness() { return hardness; }
    public float resistance() { return resistance; }
}
