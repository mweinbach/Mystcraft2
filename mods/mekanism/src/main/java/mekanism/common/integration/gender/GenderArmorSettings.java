package mekanism.common.integration.gender;

/**
 * The chest-armor values upstream reports to Wildfire's Gender Mod. The numbers are the 10.3.9 settings.
 */
public record GenderArmorSettings(boolean coversBreasts, boolean alwaysHidesBreasts, float physicsResistance, float tightness) {

    public static final GenderArmorSettings OPEN_FRONT = new GenderArmorSettings(false, false, 0, 0);
    public static final GenderArmorSettings HIDES_BREASTS = new GenderArmorSettings(true, true, 0, 0);
    public static final GenderArmorSettings HAZMAT = covering(0.5F, 0.25F);
    public static final GenderArmorSettings BRONZE = covering(0.9F, 0);
    public static final GenderArmorSettings LAPIS_LAZULI = covering(0.6F, 0.1F);
    public static final GenderArmorSettings OSMIUM = covering(1, 0);
    public static final GenderArmorSettings REFINED_GLOWSTONE = covering(0.95F, 0);
    public static final GenderArmorSettings REFINED_OBSIDIAN = covering(1, 0);
    public static final GenderArmorSettings STEEL = covering(1, 0);

    public GenderArmorSettings {
        if (physicsResistance < 0 || physicsResistance > 1) {
            throw new IllegalArgumentException("Physics resistance must be between zero and one inclusive.");
        } else if (tightness < 0 || tightness > 1) {
            throw new IllegalArgumentException("Armor tightness must be between zero and one inclusive.");
        }
    }

    public static GenderArmorSettings covering(float physicsResistance, float tightness) {
        return new GenderArmorSettings(true, false, physicsResistance, tightness);
    }
}
