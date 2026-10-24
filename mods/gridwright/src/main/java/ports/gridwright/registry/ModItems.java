package ports.gridwright.registry;

import ports.gridwright.Gridwright;
import ports.gridwright.platform.Platform;
import net.minecraft.core.Registry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class ModItems {

    /** Made by the resonator from redstone; the crafting key to the second tier of blocks. */
    public static final Supplier<Item> GRID_CRYSTAL = item("grid_crystal");
    /** The common casing of the fuel generators (except the survivalist) and the tier-two producers. */
    public static final Supplier<Item> MACHINE_FRAME = item("machine_frame");

    static {
        ModBlocks.BLOCKS.forEach((name, block) -> Platform.register(Registry.ITEM_REGISTRY, name,
                () -> new BlockItem(block.get(), new Item.Properties().tab(Gridwright.TAB))));
    }

    private ModItems() { }

    public static void init() { }

    private static Supplier<Item> item(String name) {
        return Platform.register(Registry.ITEM_REGISTRY, name, () -> new Item(new Item.Properties().tab(Gridwright.TAB)));
    }
}
