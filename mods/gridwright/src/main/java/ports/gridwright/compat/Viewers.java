package ports.gridwright.compat;

/**
 * Loader-neutral checks for which recipe viewers are installed, by API class presence. Used to keep
 * JEI's plugin quiet when EMI is installed too (EMI shows JEI plugins' categories itself, so both
 * would otherwise list resonating twice).
 */
public final class Viewers {

    public static final String JEI_API = "mezz.jei.api.IModPlugin";
    public static final String EMI_API = "dev.emi.emi.api.EmiPlugin";
    public static final String REI_API = "me.shedaniel.rei.api.common.display.Display";

    private Viewers() { }

    public static boolean present(String apiClass) {
        try {
            Class.forName(apiClass, false, Viewers.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static boolean emi() {
        return present(EMI_API);
    }
}
