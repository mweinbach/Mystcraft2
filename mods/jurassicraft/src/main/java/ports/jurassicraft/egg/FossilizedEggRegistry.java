package ports.jurassicraft.egg;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import java.util.List;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** The old egg damage values 0, 1 and 2 are now distinct item IDs. */
public final class FossilizedEggRegistry {
    public static final Supplier<Item> FOSSILIZED_EGG = egg("fossilized_egg");
    public static final Supplier<Item> FOSSILIZED_EGG_2 = egg("fossilized_egg_2");
    public static final Supplier<Item> FOSSILIZED_EGG_3 = egg("fossilized_egg_3");
    private static final List<Supplier<Item>> VARIANTS = List.of(FOSSILIZED_EGG, FOSSILIZED_EGG_2, FOSSILIZED_EGG_3);

    private FossilizedEggRegistry() {}

    private static Supplier<Item> egg(String name) {
        return Platform.register(Registry.ITEM_REGISTRY, name, () -> new FossilizedEggItem(new Item.Properties().tab(JurassicContent.TAB)));
    }

    /** Returns the egg corresponding to the original zero-based nest variant. */
    public static Supplier<Item> variant(int legacyVariant) { return VARIANTS.get(legacyVariant); }

    public static void init() { }
}
