package ports.gridwright.power;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** Which way a power converter works. Sneak-use it with an empty hand to switch. */
public enum ConverterMode implements StringRepresentable {
    /** Draws Grid Power from its owner's grid and gives Forge Energy / Team Reborn Energy. */
    TO_ENERGY,
    /** Takes Forge Energy / Team Reborn Energy and adds Grid Power to its owner's grid. */
    TO_GRID;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ConverterMode next() {
        return this == TO_ENERGY ? TO_GRID : TO_ENERGY;
    }
}
