package ports.jurassicraft.plantculture;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.plants.PlantSpecies;

/** Species-specific plant calluses for the embryonic machine and planting. */
public final class PlantCultureRegistry {
    private static final Map<PlantSpecies, Supplier<Item>> CALLUSES = new EnumMap<>(PlantSpecies.class);
    static {
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric())
            CALLUSES.put(species, item(species));
    }
    private PlantCultureRegistry() {}
    public static Supplier<Item> callus(PlantSpecies species) { return CALLUSES.get(species); }
    public static void init() {}
    private static Supplier<Item> item(PlantSpecies species) {
        Item item = new PlantCallusItem(species, new Item.Properties().tab(JurassicContent.TAB));
        Registry.register(Registry.ITEM, new ResourceLocation(PortMod.MOD_ID, species.id() + "_plant_callus"), item);
        return () -> item;
    }
}
