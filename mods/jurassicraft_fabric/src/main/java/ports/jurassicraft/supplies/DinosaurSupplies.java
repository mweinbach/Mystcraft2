package ports.jurassicraft.supplies;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DinosaurSupplies {
    public static final Supplier<Item> STEAK = item("dinosaur_steak", new Item(properties()
            .food(new FoodProperties.Builder().nutrition(8).saturationMod(.8F).meat().build())));
    public static final Supplier<Item> GROWTH_SERUM = item("growth_serum", new GrowthSerumItem(properties()));
    public static final Supplier<Item> PREGNANCY_TEST = item("pregnancy_test", new PregnancyTestItem(properties()));
    public static final Supplier<Item> BREEDING_WAND = item("breeding_wand", new BreedingWandItem(properties().stacksTo(1)));
    public static final Supplier<Item> BIRTHING_WAND = item("birthing_wand", new BirthingWandItem(properties().stacksTo(1)));
    private DinosaurSupplies() {}
    private static Item.Properties properties() { return new Item.Properties().tab(JurassicContent.TAB); }
    private static Supplier<Item> item(String name, Item item) {
        Registry.register(Registry.ITEM, new ResourceLocation(PortMod.MOD_ID, name), item);
        return () -> item;
    }
    public static void init() {}
}
