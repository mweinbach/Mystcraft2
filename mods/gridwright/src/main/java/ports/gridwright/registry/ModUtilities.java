package ports.gridwright.registry;

import ports.gridwright.Gridwright;
import ports.gridwright.block.CompressedKind;
import ports.gridwright.block.DrumBlock;
import ports.gridwright.block.DrumTier;
import ports.gridwright.block.SoundMufflerBlock;
import ports.gridwright.block.SpikeBlock;
import ports.gridwright.block.SpikeKind;
import ports.gridwright.block.TrashCanBlock;
import ports.gridwright.block.TrashKind;
import ports.gridwright.item.AngelRingItem;
import ports.gridwright.item.BagMenu;
import ports.gridwright.item.BagOfHoldingItem;
import ports.gridwright.item.BuildersWandItem;
import ports.gridwright.item.DestructionWandItem;
import ports.gridwright.item.GoldenLassoItem;
import ports.gridwright.item.PocketWorkbenchItem;
import ports.gridwright.item.WateringCanItem;
import ports.gridwright.platform.Platform;
import ports.gridwright.storage.DrumBlockEntity;
import ports.gridwright.storage.SoundMufflerBlockEntity;
import ports.gridwright.storage.TrashCanBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Phase 2b: the utility items and blocks (Angel Ring, lasso, wands, watering can, bag, workbench,
 * drums, trash cans, compressed blocks, spikes and the sound muffler). Kept apart from the phase 1
 * registries so later phases can add their own without touching these lines.
 */
public final class ModUtilities {

    private static final Map<String, Supplier<? extends Block>> ALL_BLOCKS = new LinkedHashMap<>();
    /** Every phase 2b block by registry path, in registration order; each gets a block item. */
    public static final Map<String, Supplier<? extends Block>> BLOCKS = Collections.unmodifiableMap(ALL_BLOCKS);

    // Blocks
    public static final Map<CompressedKind, List<Supplier<Block>>> COMPRESSED = compressedBlocks();
    public static final Map<SpikeKind, Supplier<SpikeBlock>> SPIKES = spikes();
    public static final Supplier<SoundMufflerBlock> SOUND_MUFFLER = block("sound_muffler", () -> new SoundMufflerBlock(
            BlockBehaviour.Properties.of(Material.WOOL).strength(0.8F).sound(SoundType.WOOL)));
    public static final Map<DrumTier, Supplier<DrumBlock>> DRUMS = drums();
    public static final Map<TrashKind, Supplier<TrashCanBlock>> TRASH_CANS = trashCans();

    // Items
    public static final Supplier<AngelRingItem> ANGEL_RING = item("angel_ring",
            () -> new AngelRingItem(properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Supplier<GoldenLassoItem> GOLDEN_LASSO = item("golden_lasso",
            () -> new GoldenLassoItem(properties().stacksTo(1)));
    public static final Supplier<BuildersWandItem> BUILDERS_WAND = item("builders_wand",
            () -> new BuildersWandItem(properties().stacksTo(1)));
    public static final Supplier<DestructionWandItem> DESTRUCTION_WAND = item("destruction_wand",
            () -> new DestructionWandItem(properties().stacksTo(1)));
    public static final Supplier<WateringCanItem> WATERING_CAN = item("watering_can",
            () -> new WateringCanItem(properties().stacksTo(1)));
    public static final Supplier<BagOfHoldingItem> BAG_OF_HOLDING = item("bag_of_holding",
            () -> new BagOfHoldingItem(properties().stacksTo(1)));
    public static final Supplier<PocketWorkbenchItem> POCKET_WORKBENCH = item("pocket_workbench",
            () -> new PocketWorkbenchItem(properties().stacksTo(1)));

    static {
        BLOCKS.forEach((name, block) -> Platform.register(Registry.ITEM_REGISTRY, name,
                () -> new BlockItem(block.get(), properties())));
    }

    // Block entities and menus
    public static final Supplier<BlockEntityType<DrumBlockEntity>> DRUM = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "drum", () -> BlockEntityType.Builder.of(DrumBlockEntity::new,
                    DRUMS.values().stream().map(Supplier::get).toArray(Block[]::new)).build(null));
    public static final Supplier<BlockEntityType<TrashCanBlockEntity>> TRASH_CAN = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "trash_can", () -> BlockEntityType.Builder.of(TrashCanBlockEntity::new,
                    TRASH_CANS.values().stream().map(Supplier::get).toArray(Block[]::new)).build(null));
    public static final Supplier<BlockEntityType<SoundMufflerBlockEntity>> SOUND_MUFFLER_ENTITY = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "sound_muffler", () -> BlockEntityType.Builder.of(SoundMufflerBlockEntity::new,
                    SOUND_MUFFLER.get()).build(null));
    public static final Supplier<MenuType<BagMenu>> BAG_MENU = Platform.menu("bag_of_holding", BagMenu::client);

    private ModUtilities() { }

    public static void init() { }

    public static String compressedName(CompressedKind kind, int tier) {
        return CompressedKind.PREFIXES[tier - 1] + "compressed_" + kind.name().toLowerCase(Locale.ROOT);
    }

    private static Map<CompressedKind, List<Supplier<Block>>> compressedBlocks() {
        Map<CompressedKind, List<Supplier<Block>>> blocks = new EnumMap<>(CompressedKind.class);
        for (CompressedKind kind : CompressedKind.values()) {
            List<Supplier<Block>> tiers = new ArrayList<>();
            for (int tier = 1; tier <= kind.tiers(); tier++) {
                int level = tier;
                tiers.add(block(compressedName(kind, tier), () -> new Block(kind.properties(level))));
            }
            blocks.put(kind, List.copyOf(tiers));
        }
        return Collections.unmodifiableMap(blocks);
    }

    private static Map<SpikeKind, Supplier<SpikeBlock>> spikes() {
        Map<SpikeKind, Supplier<SpikeBlock>> spikes = new EnumMap<>(SpikeKind.class);
        for (SpikeKind kind : SpikeKind.values()) {
            spikes.put(kind, block(kind.name().toLowerCase(Locale.ROOT) + "_spike", () -> new SpikeBlock(kind, kind.properties())));
        }
        return Collections.unmodifiableMap(spikes);
    }

    private static Map<DrumTier, Supplier<DrumBlock>> drums() {
        Map<DrumTier, Supplier<DrumBlock>> drums = new EnumMap<>(DrumTier.class);
        for (DrumTier tier : DrumTier.values()) {
            drums.put(tier, block(tier.name().toLowerCase(Locale.ROOT) + "_drum", () -> new DrumBlock(tier, tier.properties())));
        }
        return Collections.unmodifiableMap(drums);
    }

    private static Map<TrashKind, Supplier<TrashCanBlock>> trashCans() {
        Map<TrashKind, Supplier<TrashCanBlock>> cans = new EnumMap<>(TrashKind.class);
        for (TrashKind kind : TrashKind.values()) {
            cans.put(kind, block(kind.registryName(), () -> new TrashCanBlock(kind,
                    BlockBehaviour.Properties.of(Material.STONE).strength(2.0F).sound(SoundType.STONE).requiresCorrectToolForDrops())));
        }
        return Collections.unmodifiableMap(cans);
    }

    private static Item.Properties properties() {
        return new Item.Properties().tab(Gridwright.TAB);
    }

    private static <T extends Item> Supplier<T> item(String name, Supplier<T> item) {
        return Platform.register(Registry.ITEM_REGISTRY, name, item);
    }

    private static <T extends Block> Supplier<T> block(String name, Supplier<T> block) {
        Supplier<T> entry = Platform.register(Registry.BLOCK_REGISTRY, name, block);
        ALL_BLOCKS.put(name, entry);
        return entry;
    }
}
