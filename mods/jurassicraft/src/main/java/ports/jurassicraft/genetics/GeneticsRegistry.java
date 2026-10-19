package ports.jurassicraft.genetics;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.plants.PlantSpecies;

/** Registrations for both animal and plant genetics. */
public final class GeneticsRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    public static final RegistryObject<Item> STORAGE_DISC = ITEMS.register("storage_disc", () -> new GeneticsItem(new Item.Properties().tab(JurassicContent.TAB)));
    private static final Map<Species, RegistryObject<Item>> TISSUE = new EnumMap<>(Species.class);
    private static final Map<Species, RegistryObject<Item>> DNA = new EnumMap<>(Species.class);
    private static final Map<PlantSpecies, RegistryObject<Item>> PLANT_TISSUE = new EnumMap<>(PlantSpecies.class);
    private static final Map<PlantSpecies, RegistryObject<Item>> PLANT_DNA = new EnumMap<>(PlantSpecies.class);

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
    private static RegistryObject<Item> item(String name, int size) {
        return ITEMS.register(name, () -> new GeneticsItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(size)));
    }
    public static RegistryObject<Item> softTissue(Species species) { return TISSUE.get(species); }
    public static RegistryObject<Item> dinosaurDna(Species species) { return DNA.get(species); }
    public static RegistryObject<Item> plantTissue(PlantSpecies species) { return PLANT_TISSUE.get(species); }
    public static RegistryObject<Item> plantDna(PlantSpecies species) { return PLANT_DNA.get(species); }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
