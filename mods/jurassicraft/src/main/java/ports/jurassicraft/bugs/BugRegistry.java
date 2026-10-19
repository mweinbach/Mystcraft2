package ports.jurassicraft.bugs;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class BugRegistry {
    public static final Supplier<Block> CRATE=Platform.register(Registry.BLOCK_REGISTRY, "bug_crate",BugCrateBlock::new);
    public static final Supplier<Block> PLANKTON_SWARM=Platform.register(Registry.BLOCK_REGISTRY, "plankton_swarm",() -> new SwarmBlock(BugRegistry::plankton));
    public static final Supplier<Block> KRILL_SWARM=Platform.register(Registry.BLOCK_REGISTRY, "krill_swarm",() -> new SwarmBlock(BugRegistry::krill));
    public static final Supplier<Item> CRATE_ITEM=Platform.register(Registry.ITEM_REGISTRY, "bug_crate",() -> new BlockItem(CRATE.get(),properties()));
    public static final Supplier<Item> CRICKETS=Platform.register(Registry.ITEM_REGISTRY, "crickets",() -> new Item(properties()));
    public static final Supplier<Item> COCKROACHES=Platform.register(Registry.ITEM_REGISTRY, "cockroaches",() -> new Item(properties()));
    public static final Supplier<Item> MEALWORM_BEETLES=Platform.register(Registry.ITEM_REGISTRY, "mealworm_beetles",() -> new Item(properties()));
    public static final Supplier<Item> FINE_NET=Platform.register(Registry.ITEM_REGISTRY, "fine_net",() -> new FineNetItem(properties().durability(50)));
    public static final Supplier<Item> PLANKTON=Platform.register(Registry.ITEM_REGISTRY, "plankton",() -> new SwarmItem(properties(),() -> PLANKTON_SWARM.get()));
    public static final Supplier<Item> KRILL=Platform.register(Registry.ITEM_REGISTRY, "krill",() -> new SwarmItem(properties(),() -> KRILL_SWARM.get()));
    public static final Supplier<BlockEntityType<BugCrateBlockEntity>> ENTITY=Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "bug_crate",() -> BlockEntityType.Builder.of(BugCrateBlockEntity::new,CRATE.get()).build(null));
    public static final Supplier<MenuType<BugCrateMenu>> MENU=Platform.register(Registry.MENU_REGISTRY, "bug_crate",() -> Platform.menuType(BugCrateMenu::new));
    private BugRegistry() {}
    private static Item plankton() { return PLANKTON.get(); }
    private static Item krill() { return KRILL.get(); }
    private static Item.Properties properties() { return new Item.Properties().tab(JurassicContent.TAB); }
    public static void init() { }
}
