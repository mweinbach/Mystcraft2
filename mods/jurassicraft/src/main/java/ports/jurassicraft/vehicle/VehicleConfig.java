package ports.jurassicraft.vehicle;

import ports.jurassicraft.config.ConfigOption;

/** Vehicle settings live in separate common and client configuration files. */
public final class VehicleConfig {
    public static final ConfigOption<Boolean> DESTROY_BLOCKS = ConfigOption.bool(ConfigOption.File.VEHICLES, null, "destroyBlocks", true, false,
            "Vehicles remove leaves and vines in their path.");
    public static final ConfigOption<Boolean> HELICOPTER_ZOOM = ConfigOption.bool(ConfigOption.File.VEHICLE_CLIENT, null, "helicopterZoomout", false, false,
            "Allow Page Up and Page Down to adjust the helicopter's third-person camera.");
    private VehicleConfig() {}
    public static void load() {}
}
