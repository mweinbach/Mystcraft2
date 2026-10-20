package ports.buildcraft.energy;

import java.util.List;

/**
 * One row of upstream {@code BCEnergyFluids}' table: a fluid registered at the three heat stages the
 * heat exchanger moves it between. The sprites of every stage are recoloured with the same colours.
 */
public final class BCFluidFamily {
    public static final int HEATS = 3;

    public final String name;
    public final int density;
    public final int viscosity;
    /** The first heat stage at which the fluid is a gas. */
    public final int boilPoint;
    public final int spread;
    public final int lightColour;
    public final int darkColour;
    private final List<BCFluidDefinition> heats;

    BCFluidFamily(String name, int density, int viscosity, int boilPoint, int spread, int lightColour, int darkColour) {
        this.name = name;
        this.density = density;
        this.viscosity = viscosity;
        this.boilPoint = boilPoint;
        this.spread = spread;
        this.lightColour = lightColour;
        this.darkColour = darkColour;
        this.heats = List.of(new BCFluidDefinition(this, 0), new BCFluidDefinition(this, 1), new BCFluidDefinition(this, 2));
    }

    public BCFluidDefinition heat(int heat) {
        return heats.get(heat);
    }

    public List<BCFluidDefinition> heats() {
        return heats;
    }
}
