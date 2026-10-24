package ports.gridwright.registry;

import ports.gridwright.Gridwright;
import ports.gridwright.platform.Platform;
import ports.gridwright.power.ConverterBlock;
import ports.gridwright.power.ConverterBlockEntity;
import ports.gridwright.power.MillBlock;
import ports.gridwright.power.MillBlockEntity;
import ports.gridwright.power.MillKind;
import ports.gridwright.power.Overclock;
import ports.gridwright.power.OverclockUpgradeItem;
import ports.gridwright.power.RainbowGeneratorBlock;
import ports.gridwright.power.RainbowGeneratorBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Phase 2d: extra Grid Power producers (fire, lava, manual and dragon egg mills, the rainbow
 * generator), the lossy GP/energy converter and generator overclock upgrades. Kept apart from the
 * earlier registries so parallel phases do not touch the same lines.
 */
public final class ModPower {

    private static final Map<String, Supplier<? extends Block>> ALL_BLOCKS = new LinkedHashMap<>();
    /** Every phase 2d block by registry path, in registration order; each gets a block item. */
    public static final Map<String, Supplier<? extends Block>> BLOCKS = Collections.unmodifiableMap(ALL_BLOCKS);

    public static final Map<MillKind, Supplier<MillBlock>> MILLS = mills();
    public static final Supplier<RainbowGeneratorBlock> RAINBOW_GENERATOR = block("rainbow_generator",
            () -> new RainbowGeneratorBlock(BlockBehaviour.Properties.of(Material.METAL).strength(3.5F)
                    .sound(SoundType.METAL).lightLevel(state -> 7)));
    public static final Supplier<ConverterBlock> POWER_CONVERTER = block("power_converter",
            () -> new ConverterBlock(BlockBehaviour.Properties.of(Material.METAL).strength(3.5F).sound(SoundType.METAL)));

    public static final Supplier<OverclockUpgradeItem> OVERCLOCK_UPGRADE = Platform.register(Registry.ITEM_REGISTRY,
            "overclock_upgrade", () -> new OverclockUpgradeItem(properties().stacksTo(Overclock.MAX)));

    static {
        BLOCKS.forEach((name, block) -> Platform.register(Registry.ITEM_REGISTRY, name, () -> new BlockItem(block.get(),
                name.equals("rainbow_generator") ? properties().rarity(Rarity.UNCOMMON) : properties())));
    }

    public static final Supplier<BlockEntityType<MillBlockEntity>> MILL = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "mill", () -> BlockEntityType.Builder.of(MillBlockEntity::new,
                    MILLS.values().stream().map(Supplier::get).toArray(Block[]::new)).build(null));
    public static final Supplier<BlockEntityType<RainbowGeneratorBlockEntity>> RAINBOW_GENERATOR_ENTITY = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "rainbow_generator", () -> BlockEntityType.Builder.of(
                    RainbowGeneratorBlockEntity::new, RAINBOW_GENERATOR.get()).build(null));
    public static final Supplier<BlockEntityType<ConverterBlockEntity>> POWER_CONVERTER_ENTITY = Platform.register(
            Registry.BLOCK_ENTITY_TYPE_REGISTRY, "power_converter", () -> BlockEntityType.Builder.of(
                    ConverterBlockEntity::new, POWER_CONVERTER.get()).build(null));

    private ModPower() { }

    public static void init() { }

    public static String millName(MillKind kind) {
        return kind.name().toLowerCase(Locale.ROOT) + "_mill";
    }

    private static Map<MillKind, Supplier<MillBlock>> mills() {
        Map<MillKind, Supplier<MillBlock>> mills = new EnumMap<>(MillKind.class);
        for (MillKind kind : MillKind.values()) {
            boolean wooden = kind == MillKind.MANUAL;
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of(wooden ? Material.WOOD : Material.METAL)
                    .strength(wooden ? 2.0F : 3.5F).sound(wooden ? SoundType.WOOD : SoundType.METAL);
            mills.put(kind, block(millName(kind), () -> new MillBlock(kind, properties)));
        }
        return Collections.unmodifiableMap(mills);
    }

    private static Item.Properties properties() {
        return new Item.Properties().tab(Gridwright.TAB);
    }

    private static <T extends Block> Supplier<T> block(String name, Supplier<T> block) {
        Supplier<T> entry = Platform.register(Registry.BLOCK_REGISTRY, name, block);
        ALL_BLOCKS.put(name, entry);
        return entry;
    }
}
