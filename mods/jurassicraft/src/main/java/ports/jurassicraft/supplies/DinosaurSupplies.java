package ports.jurassicraft.supplies;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class DinosaurSupplies {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    public static final RegistryObject<Item> STEAK = ITEMS.register("dinosaur_steak", () -> new Item(
            new Item.Properties().tab(JurassicContent.TAB).food(new FoodProperties.Builder().nutrition(8).saturationMod(.8F).meat().build())));
    public static final RegistryObject<Item> GROWTH_SERUM = ITEMS.register("growth_serum",
            () -> new GrowthSerumItem(new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> PREGNANCY_TEST = ITEMS.register("pregnancy_test",
            () -> new PregnancyTestItem(new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> BREEDING_WAND = ITEMS.register("breeding_wand",
            () -> new BreedingWandItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    public static final RegistryObject<Item> BIRTHING_WAND = ITEMS.register("birthing_wand",
            () -> new BirthingWandItem(new Item.Properties().tab(JurassicContent.TAB).stacksTo(1)));
    private DinosaurSupplies() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); }
}
