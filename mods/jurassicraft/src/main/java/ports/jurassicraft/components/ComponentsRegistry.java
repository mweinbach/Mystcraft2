package ports.jurassicraft.components;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Crafting components shared by the laboratory machines. */
public final class ComponentsRegistry {
    public static final Supplier<Item> BASIC_CIRCUIT = item("basic_circuit");
    public static final Supplier<Item> ADVANCED_CIRCUIT = item("advanced_circuit");
    public static final Supplier<Item> COMPUTER_SCREEN = item("computer_screen");
    public static final Supplier<Item> KEYBOARD = item("keyboard");
    public static final Supplier<Item> LASER = item("laser");
    public static final Supplier<Item> DNA_ANALYZER = item("dna_analyzer");
    public static final Supplier<Item> DISC_READER = item("disc_reader");
    public static final Supplier<Item> EMPTY_TEST_TUBE = item("empty_test_tube");
    public static final Supplier<Item> DNA_NUCLEOTIDES = item("dna_nucleotides");
    public static final Supplier<Item> PETRI_DISH = item("petri_dish");
    public static final Supplier<Item> PETRI_DISH_AGAR = item("petri_dish_agar");
    public static final Supplier<Item> LIQUID_AGAR = item("liquid_agar");
    public static final Supplier<Item> PLANT_CELLS = item("plant_cells");
    public static final Supplier<Item> PLANT_CELLS_PETRI_DISH = item("plant_cells_petri_dish");
    public static final Supplier<Item> EMPTY_SYRINGE = Platform.register(Registry.ITEM_REGISTRY, "empty_syringe",
            () -> new EmptySyringeItem(new Item.Properties().tab(JurassicContent.TAB)));

    private ComponentsRegistry() {}
    public static void init() { }
    private static Supplier<Item> item(String id) {
        return Platform.register(Registry.ITEM_REGISTRY, id, () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    }
}
