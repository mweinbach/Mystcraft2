package ports.jurassicraft.vehicle;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Vehicle settings live in a separate common configuration file. */
public final class VehicleConfig {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue DESTROY_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue HELICOPTER_ZOOM;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        DESTROY_BLOCKS = builder.comment("Vehicles remove leaves and vines in their path.").define("destroyBlocks", true);
        SPEC = builder.build();
        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();
        HELICOPTER_ZOOM = client.comment("Allow Page Up and Page Down to adjust the helicopter's third-person camera.")
                .define("helicopterZoomout", false);
        CLIENT_SPEC = client.build();
    }
    private VehicleConfig() {}
    static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "jurassicraft-vehicles.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, "jurassicraft-vehicle-client.toml");
    }
}
