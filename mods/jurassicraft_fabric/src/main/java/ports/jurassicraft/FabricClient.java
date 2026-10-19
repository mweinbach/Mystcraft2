package ports.jurassicraft;

import net.fabricmc.api.ClientModInitializer;
import ports.jurassicraft.assembly.client.AssemblyClient;
import ports.jurassicraft.bugs.BugClient;
import ports.jurassicraft.cleaning.client.CleaningStationClient;
import ports.jurassicraft.client.DinosaurClient;
import ports.jurassicraft.cultivator.CultivatorClient;
import ports.jurassicraft.feeder.client.FeederClient;
import ports.jurassicraft.grinder.FossilGrinderClient;
import ports.jurassicraft.hatching.HatcheryClient;
import ports.jurassicraft.machine.DnaMachineClient;
import ports.jurassicraft.plants.client.PlantColors;
import ports.jurassicraft.skull.client.SkullClient;
import ports.jurassicraft.tourrail.client.TourRailClient;
import ports.jurassicraft.vehicle.client.VehicleClient;
import ports.jurassicraft.goat.client.GoatClient;
import ports.jurassicraft.darts.DartClient;
import ports.jurassicraft.fieldguide.client.FieldGuideClient;
import ports.jurassicraft.journal.client.JournalClient;
import ports.jurassicraft.figure.client.ActionFigureClient;
import ports.jurassicraft.mural.client.MuralClient;
import ports.jurassicraft.model.client.MachineModels;
import ports.jurassicraft.village.client.VillageClient;
import ports.jurassicraft.decor.client.DecorClient;

public final class FabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        PlantColors.init();
        AssemblyClient.init();
        BugClient.init();
        CleaningStationClient.init();
        CultivatorClient.init();
        DinosaurClient.init();
        FieldGuideClient.init();
        JournalClient.init();
        ActionFigureClient.init();
        MuralClient.init();
        DecorClient.init();
        GoatClient.init();
        DartClient.init();
        FeederClient.init();
        FossilGrinderClient.init();
        HatcheryClient.init();
        DnaMachineClient.init();
        SkullClient.init();
        TourRailClient.init();
        VehicleClient.init();
        MachineModels.init();
        VillageClient.init();
    }
}
