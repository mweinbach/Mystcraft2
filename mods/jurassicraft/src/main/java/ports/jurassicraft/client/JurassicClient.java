package ports.jurassicraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import ports.jurassicraft.assembly.client.AssemblyClient;
import ports.jurassicraft.bugs.BugClient;
import ports.jurassicraft.cleaning.client.CleaningStationClient;
import ports.jurassicraft.cultivator.CultivatorClient;
import ports.jurassicraft.darts.DartClient;
import ports.jurassicraft.decor.client.DecorClient;
import ports.jurassicraft.feeder.client.FeederClient;
import ports.jurassicraft.fieldguide.client.FieldGuideClient;
import ports.jurassicraft.figure.client.ActionFigureClient;
import ports.jurassicraft.goat.client.GoatClient;
import ports.jurassicraft.grinder.FossilGrinderClient;
import ports.jurassicraft.hatching.HatcheryClient;
import ports.jurassicraft.journal.client.JournalClient;
import ports.jurassicraft.machine.DnaMachineClient;
import ports.jurassicraft.mural.client.MuralClient;
import ports.jurassicraft.plants.client.PlantColors;
import ports.jurassicraft.platform.client.ClientPlatform;
import ports.jurassicraft.skull.client.SkullClient;
import ports.jurassicraft.tourrail.client.TourRailClient;
import ports.jurassicraft.vehicle.client.HelicopterHud;
import ports.jurassicraft.vehicle.client.VehicleCamera;
import ports.jurassicraft.vehicle.client.VehicleClient;
import ports.jurassicraft.village.client.VillageClient;

/**
 * Client phases in the order loaders run them: {@link #keys} and {@link #renderers} during mod
 * loading, {@link #setup} once registries are populated, {@link #colors} when color handlers are
 * built, then the per-frame hooks.
 */
public final class JurassicClient {
    private JurassicClient() {}

    public static void keys() {
        DinosaurClient.keys();
        VehicleClient.keys();
    }

    public static void renderers(ClientPlatform.Renderers renderers) {
        AssemblyClient.renderers(renderers);
        CleaningStationClient.renderers(renderers);
        DinosaurClient.renderers(renderers);
        DartClient.renderers(renderers);
        DecorClient.renderers(renderers);
        FeederClient.renderers(renderers);
        ActionFigureClient.renderers(renderers);
        GoatClient.renderers(renderers);
        HatcheryClient.renderers(renderers);
        DnaMachineClient.renderers(renderers);
        MuralClient.renderers(renderers);
        SkullClient.renderers(renderers);
        TourRailClient.renderers(renderers);
        VehicleClient.renderers(renderers);
        VillageClient.renderers(renderers);
    }

    /** Menu screens, render layers, item properties and client packet handlers; runs on the client thread. */
    public static void setup() {
        PlantColors.setup();
        AssemblyClient.setup();
        BugClient.setup();
        CleaningStationClient.setup();
        CultivatorClient.setup();
        DinosaurClient.setup();
        DecorClient.setup();
        FeederClient.setup();
        FieldGuideClient.setup();
        ActionFigureClient.setup();
        FossilGrinderClient.setup();
        HatcheryClient.setup();
        JournalClient.setup();
        DnaMachineClient.setup();
        VehicleClient.setup();
    }

    public static void colors(ClientPlatform.Colors colors) {
        PlantColors.colors(colors);
        DartClient.colors(colors);
    }

    /** Runs at the start of the client tick, before the local player samples jump and sprint. */
    public static void beforeTick(Minecraft client) { VehicleClient.beforeTick(client); }

    public static void tick(Minecraft client) {
        DinosaurClient.tick(client);
        VehicleClient.tick(client);
    }

    public static void hud(PoseStack pose, float partialTick) { HelicopterHud.render(pose, partialTick); }

    public static void camera(Camera camera, float partialTick) { VehicleCamera.apply(camera, partialTick); }
}
