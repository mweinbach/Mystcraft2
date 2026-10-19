package ports.jurassicraft.components;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Crafting components shared by the laboratory machines. */
public final class ComponentsRegistry {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    public static final RegistryObject<Item> BASIC_CIRCUIT = item("basic_circuit");
    public static final RegistryObject<Item> ADVANCED_CIRCUIT = item("advanced_circuit");
    public static final RegistryObject<Item> COMPUTER_SCREEN = item("computer_screen");
    public static final RegistryObject<Item> KEYBOARD = item("keyboard");
    public static final RegistryObject<Item> LASER = item("laser");
    public static final RegistryObject<Item> DNA_ANALYZER = item("dna_analyzer");
    public static final RegistryObject<Item> DISC_READER = item("disc_reader");
    public static final RegistryObject<Item> EMPTY_TEST_TUBE = item("empty_test_tube");
    public static final RegistryObject<Item> DNA_NUCLEOTIDES = item("dna_nucleotides");
    public static final RegistryObject<Item> PETRI_DISH = item("petri_dish");
    public static final RegistryObject<Item> PETRI_DISH_AGAR = item("petri_dish_agar");
    public static final RegistryObject<Item> LIQUID_AGAR = item("liquid_agar");
    public static final RegistryObject<Item> PLANT_CELLS = item("plant_cells");
    public static final RegistryObject<Item> PLANT_CELLS_PETRI_DISH = item("plant_cells_petri_dish");
    public static final RegistryObject<Item> EMPTY_SYRINGE = ITEMS.register("empty_syringe",
            () -> new EmptySyringeItem(new Item.Properties().tab(JurassicContent.TAB)));

    private ComponentsRegistry() {}
    public static void register(IEventBus bus) { ITEMS.register(bus); }
    private static RegistryObject<Item> item(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    }
}
