package ports.jurassicraft.genetics;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.plants.PlantSpecies;

/** Registrations for both animal and plant genetics. */
public final class GeneticsRegistry {
    public static final Supplier<Item> STORAGE_DISC = Platform.register(Registry.ITEM_REGISTRY, "storage_disc", () -> new GeneticsItem(new Item.Properties().tab(JurassicContent.TAB)));
    private static final Map<Species, Supplier<Item>> TISSUE = new EnumMap<>(Species.class);
    private static final Map<Species, Supplier<Item>> DNA = new EnumMap<>(Species.class);
    private static final Map<PlantSpecies, Supplier<Item>> PLANT_TISSUE = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, Supplier<Item>> PLANT_DNA = new EnumMap<>(PlantSpecies.class);

    static {
        for (Species species : Species.values()) {
            TISSUE.put(species, item(species.id() + "_soft_tissue", 64));
            DNA.put(species, item(species.id() + "_dna", 1));
        }
        for (PlantSpecies species : PlantSpecies.values()) {
            if (species.isPrehistoric()) {
                PLANT_TISSUE.put(species, item(species.id() + "_plant_tissue", 64));
                PLANT_DNA.put(species, item(species.id() + "_plant_dna", 64));
            }
        }
    }

    private GeneticsRegistry() {}
    private static Supplier<Item> item(String name, int size) {
        return Platform.register(Registry.ITEM_REGISTRY, name, () -> new GeneticsItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(size)));
    }
    public static Supplier<Item> softTissue(Species species) { return TISSUE.get(species); }
    public static Supplier<Item> dinosaurDna(Species species) { return DNA.get(species); }
    public static Supplier<Item> plantTissue(PlantSpecies species) { return PLANT_TISSUE.get(species); }
    public static Supplier<Item> plantDna(PlantSpecies species) { return PLANT_DNA.get(species); }
    public static void init() { }
}
