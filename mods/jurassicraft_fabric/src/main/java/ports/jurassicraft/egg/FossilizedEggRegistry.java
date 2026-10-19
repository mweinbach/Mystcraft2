package ports.jurassicraft.egg;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** The legacy egg metadata variants have separate item IDs. */
public final class FossilizedEggRegistry {
    public static final Supplier<Item> FOSSILIZED_EGG = egg("fossilized_egg");
    public static final Supplier<Item> FOSSILIZED_EGG_2 = egg("fossilized_egg_2");
    public static final Supplier<Item> FOSSILIZED_EGG_3 = egg("fossilized_egg_3");
    private static final List<Supplier<Item>> VARIANTS = List.of(FOSSILIZED_EGG, FOSSILIZED_EGG_2, FOSSILIZED_EGG_3);

    private FossilizedEggRegistry() {}
    private static Supplier<Item> egg(String name) {
        Item egg = Registry.register(Registry.ITEM, new ResourceLocation(PortMod.MOD_ID, name),
                new FossilizedEggItem(new Item.Properties().tab(JurassicContent.TAB)));
        return () -> egg;
    }
    public static Supplier<Item> variant(int variant) { return VARIANTS.get(variant); }
    public static void init() {}
}
