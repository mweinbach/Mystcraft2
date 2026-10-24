package ports.gridwright.registry;

import ports.gridwright.Gridwright;
import ports.gridwright.item.BiomeMarkerItem;
import ports.gridwright.item.UpgradeItem;
import ports.gridwright.platform.Platform;
import ports.gridwright.worker.Upgrades;
import net.minecraft.core.Registry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

public final class ModItems {

    /** Made by the resonator from redstone; the crafting key to the second tier of blocks. */
    public static final Supplier<Item> GRID_CRYSTAL = item("grid_crystal");
    /** The common casing of the fuel generators (except the survivalist) and the tier-two producers. */
    public static final Supplier<Item> MACHINE_FRAME = item("machine_frame");

    // Phase 2c: quarry upgrades and the biome marker (quarries share the transfer speed upgrade).
    public static final Supplier<Item> FORTUNE_UPGRADE = Platform.register(Registry.ITEM_REGISTRY, "fortune_upgrade",
            () -> new UpgradeItem(new Item.Properties().tab(Gridwright.TAB).stacksTo(Upgrades.MAX_FORTUNE)));
    public static final Supplier<Item> SILK_TOUCH_UPGRADE = Platform.register(Registry.ITEM_REGISTRY, "silk_touch_upgrade",
            () -> new UpgradeItem(new Item.Properties().tab(Gridwright.TAB).stacksTo(1)));
    public static final Supplier<Item> BIOME_MARKER = Platform.register(Registry.ITEM_REGISTRY, "biome_marker",
            () -> new BiomeMarkerItem(new Item.Properties().tab(Gridwright.TAB).stacksTo(1)));

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
