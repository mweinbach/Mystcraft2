package ports.gridwright.transfer;

import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.gridwright.Gridwright;
import ports.gridwright.platform.Platform;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Registry entries of the transfer system: pipes, nodes, upgrades, the filter and their menus. */
public final class TransferRegistry {

    private static final Map<String, Supplier<? extends Block>> ALL = new LinkedHashMap<>();
    /** Every transfer block by registry path; each gets a block item. */
    public static final Map<String, Supplier<? extends Block>> BLOCKS = Collections.unmodifiableMap(ALL);

    public static final Supplier<TransferPipeBlock> PIPE = block("transfer_pipe", () -> new TransferPipeBlock(
            BlockBehaviour.Properties.of(Material.STONE).strength(0.5F).sound(SoundType.STONE).noOcclusion()));

    public static final Map<NodeKind, Supplier<NodeBlock>> NODES = nodes();

    public static final Supplier<BlockEntityType<NodeBlockEntity>> NODE_ENTITY = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "node", () -> BlockEntityType.Builder.of(NodeBlockEntity::new,
                    NODES.values().stream().map(Supplier::get).toArray(Block[]::new)).build(null));

    public static final Map<UpgradeKind, Supplier<UpgradeItem>> UPGRADES = upgrades();

    public static final Supplier<FilterItem> FILTER = Platform.register(Registry.ITEM_REGISTRY, "item_filter",
            () -> new FilterItem(new Item.Properties().tab(Gridwright.TAB)));

    public static final Supplier<MenuType<NodeMenu>> NODE_MENU = Platform.menu("node", NodeMenu::client);
    public static final Supplier<MenuType<FilterMenu>> FILTER_MENU = Platform.menu("filter", FilterMenu::client);

    private TransferRegistry() { }

    public static void init() { }

    private static Map<NodeKind, Supplier<NodeBlock>> nodes() {
        Map<NodeKind, Supplier<NodeBlock>> nodes = new EnumMap<>(NodeKind.class);
        for (NodeKind kind : NodeKind.values()) {
            nodes.put(kind, block(kind.id(), () -> new NodeBlock(kind,
                    BlockBehaviour.Properties.of(Material.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion())));
        }
        return Collections.unmodifiableMap(nodes);
    }

    private static Map<UpgradeKind, Supplier<UpgradeItem>> upgrades() {
        Map<UpgradeKind, Supplier<UpgradeItem>> upgrades = new EnumMap<>(UpgradeKind.class);
        for (UpgradeKind kind : UpgradeKind.values()) {
            upgrades.put(kind, Platform.register(Registry.ITEM_REGISTRY, kind.id(),
                    () -> new UpgradeItem(kind, new Item.Properties().tab(Gridwright.TAB))));
        }
        return Collections.unmodifiableMap(upgrades);
    }

    private static <T extends Block> Supplier<T> block(String name, Supplier<T> factory) {
        Supplier<T> block = Platform.register(Registry.BLOCK_REGISTRY, name, factory);
        Platform.register(Registry.ITEM_REGISTRY, name, () -> new BlockItem(block.get(), new Item.Properties().tab(Gridwright.TAB)));
        ALL.put(name, block);
        return block;
    }
}
