package ports.jurassicraft.supplies;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DinosaurSupplies {
    public static final Supplier<Item> STEAK = Platform.register(Registry.ITEM_REGISTRY, "dinosaur_steak", () -> new Item(
            new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder().nutrition(8).saturationMod(.8F).meat().build())));
    public static final Supplier<Item> GROWTH_SERUM = Platform.register(Registry.ITEM_REGISTRY, "growth_serum",
            () -> new GrowthSerumItem(new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<Item> PREGNANCY_TEST = Platform.register(Registry.ITEM_REGISTRY, "pregnancy_test",
            () -> new PregnancyTestItem(new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<Item> BREEDING_WAND = Platform.register(Registry.ITEM_REGISTRY, "breeding_wand",
            () -> new BreedingWandItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final Supplier<Item> BIRTHING_WAND = Platform.register(Registry.ITEM_REGISTRY, "birthing_wand",
            () -> new BirthingWandItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    private DinosaurSupplies() {}
    public static void init() { }
}
