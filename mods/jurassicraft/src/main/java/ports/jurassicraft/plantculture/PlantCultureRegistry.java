package ports.jurassicraft.plantculture;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.plants.PlantSpecies;

/** Species-specific plant calluses for the embryonic machine and planting. */
public final class PlantCultureRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final Map<PlantSpecies, RegistryObject<Item>> CALLUSES = new EnumMap<>(PlantSpecies.class);
    static {
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric())
            CALLUSES.put(species, ITEMS.register(species.id() + "_plant_callus", () -> new PlantCallusItem(species, new Item.Properties().tab(JurassicContent.TAB))));
    }
    private PlantCultureRegistry() {}
    public static RegistryObject<Item> callus(PlantSpecies species) { return CALLUSES.get(species); }
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
