package ports.jurassicraft.plantculture;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.plants.PlantSpecies;

/** Species-specific plant calluses for the embryonic machine and planting. */
public final class PlantCultureRegistry {
    private static final Map<PlantSpecies, Supplier<Item>> CALLUSES = new EnumMap<>(PlantSpecies.class);
    static {
        for (PlantSpecies species : PlantSpecies.values()) if (species.isPrehistoric())
            CALLUSES.put(species, Platform.register(Registry.ITEM_REGISTRY, species.id() + "_plant_callus", () -> new PlantCallusItem(species, new Item.Properties().tab(JurassicContent.TAB))));
    }
    private PlantCultureRegistry() {}
    public static Supplier<Item> callus(PlantSpecies species) { return CALLUSES.get(species); }
    public static void init() { }
}
