package ports.jurassicraft;

import ports.jurassicraft.assembly.AssemblyRegistry;
import ports.jurassicraft.bugs.BugRegistry;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.components.ComponentsRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.cultivator.CultivatorRegistry;
import ports.jurassicraft.darts.DartRegistry;
import ports.jurassicraft.decor.DecorRegistry;
import ports.jurassicraft.egg.FossilNestRegistry;
import ports.jurassicraft.egg.FossilizedEggRegistry;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.feeder.FeederRegistry;
import ports.jurassicraft.fence.FenceRegistry;
import ports.jurassicraft.fieldguide.FieldGuideRegistry;
import ports.jurassicraft.figure.ActionFigureRegistry;
import ports.jurassicraft.fossil.FossilLootEntry;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.goat.GoatRegistry;
import ports.jurassicraft.grinder.FossilGrinderRegistry;
import ports.jurassicraft.hatching.HatcheryRegistry;
import ports.jurassicraft.journal.JournalRegistry;
import ports.jurassicraft.loot.DynamicLootEntry;
import ports.jurassicraft.machine.DnaMachineRegistry;
import ports.jurassicraft.mural.MuralRegistry;
import ports.jurassicraft.music.MusicRegistry;
import ports.jurassicraft.plantculture.PlantCultureRegistry;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.scanner.DinoScannerRegistry;
import ports.jurassicraft.skull.SkullRegistry;
import ports.jurassicraft.souvenir.SouvenirRegistry;
import ports.jurassicraft.structures.StructureRegistry;
import ports.jurassicraft.supplies.DinosaurSupplies;
import ports.jurassicraft.tourrail.TourRailRegistry;
import ports.jurassicraft.vehicle.VehicleRegistry;
import ports.jurassicraft.village.VillageRegistry;
import ports.jurassicraft.worldgen.WorldgenRegistry;

/** Shared entry point; loader adapters install the platform, then call {@link #init} and {@link #setup}. */
public final class PortMod {
    public static final String MOD_ID = "jurassicraft";

    private PortMod() {}

    /** Defines every registry entry, game rule and packet. Registration itself is driven by the loader. */
    public static void init() {
        JurassicContent.init();
        FossilLootEntry.init();
        DynamicLootEntry.init();
        SkullRegistry.init();
        ComponentsRegistry.init();
        PlantRegistry.init();
        GeneticsRegistry.init();
        PlantCultureRegistry.init();
        DinosaurSupplies.init();
        AssemblyRegistry.init();
        CleaningStationRegistry.init();
        FossilGrinderRegistry.init();
        FossilizedEggRegistry.init();
        FossilNestRegistry.init();
        FenceRegistry.init();
        BugRegistry.init();
        FeederRegistry.init();
        DnaMachineRegistry.init();
        HatcheryRegistry.init();
        CultivatorRegistry.init();
        DinosaurRegistry.init();
        ActionFigureRegistry.init();
        MuralRegistry.init();
        DecorRegistry.init();
        FieldGuideRegistry.init();
        JournalRegistry.init();
        StructureRegistry.init();
        VillageRegistry.init();
        GoatRegistry.init();
        DartRegistry.init();
        DinoScannerRegistry.init();
        TourRailRegistry.init();
        VehicleRegistry.init();
        MusicRegistry.init();
        SouvenirRegistry.init();
        WorldgenRegistry.init();
    }

    /** Runs once every registry entry exists. */
    public static void setup() {
        PlantRegistry.setup();
    }
}
