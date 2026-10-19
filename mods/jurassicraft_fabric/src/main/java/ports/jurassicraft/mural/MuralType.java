package ports.jurassicraft.mural;

import java.util.Locale;

/** Pixel dimensions and stable IDs from the original artwork. */
public enum MuralType {
    CREATION_LAB(64, 32), FOSSILS(128, 64), HUNT_LEFT(64, 64), HUNT_MIDDLE(64, 64), HUNT_RIGHT(64, 64),
    MOSASAURUS(64, 32), PARK_ENTRANCE(64, 32), RIDING(64, 32), SKETCH(64, 32), TRICERATOPS(128, 32), VALLEY(64, 32);

    public final int width;
    public final int height;
    MuralType(int width, int height) { this.width = width; this.height = height; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static MuralType byId(String id) {
        for (MuralType type : values()) if (type.id().equals(id)) return type;
        return CREATION_LAB;
    }
    public static MuralType byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : CREATION_LAB;
    }
}
