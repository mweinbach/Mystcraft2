package ports.buildcraft.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.Reg;

/**
 * Engines, the oil-family fluids with their buckets, the oil spring and oil well generation (upstream
 * BuildCraft Core engine and spring plus BuildCraft Energy).
 */
public final class BCEnergy {
    private static final List<BCFluidFamily> FAMILIES = new ArrayList<>();

    // Upstream table: density, viscosity, boiling heat, spread, light colour, dark colour.
    public static final BCFluidFamily OIL = family("oil", 900, 2000, 3, 6, 0x50_50_50, 0x05_05_05);
    public static final BCFluidFamily OIL_RESIDUE = family("oil_residue", 1200, 4000, 3, 4, 0x10_0F_10, 0x42_10_42);
    public static final BCFluidFamily OIL_HEAVY = family("oil_heavy", 850, 1800, 3, 6, 0xA0_8F_1F, 0x42_35_20);
    public static final BCFluidFamily OIL_DENSE = family("oil_dense", 950, 1600, 3, 5, 0x87_6E_77, 0x42_24_24);
    public static final BCFluidFamily OIL_DISTILLED = family("oil_distilled", 750, 1400, 2, 8, 0xE4_AF_78, 0xB4_7F_00);
    public static final BCFluidFamily FUEL_DENSE = family("fuel_dense", 600, 800, 2, 7, 0xFF_AF_3F, 0xE0_7F_00);
    public static final BCFluidFamily FUEL_MIXED_HEAVY = family("fuel_mixed_heavy", 700, 1000, 2, 7, 0xF2_A7_00, 0xC4_87_00);
    public static final BCFluidFamily FUEL_LIGHT = family("fuel_light", 400, 600, 1, 8, 0xFF_FF_30, 0xE4_CF_00);
    public static final BCFluidFamily FUEL_MIXED_LIGHT = family("fuel_mixed_light", 650, 900, 1, 9, 0xF6_D7_00, 0xC4_B7_00);
    public static final BCFluidFamily FUEL_GASEOUS = family("fuel_gaseous", 300, 500, 0, 10, 0xFA_F6_30, 0xE0_D9_00);
    public static final List<BCFluidFamily> FLUID_FAMILIES = List.copyOf(FAMILIES);
    public static final List<BCFluidDefinition> FLUIDS = FLUID_FAMILIES.stream().flatMap(family -> family.heats().stream()).toList();

    public static final Supplier<OilSpringBlock> OIL_SPRING = Reg.block("oil_spring", () -> new OilSpringBlock(
            BlockBehaviour.Properties.of(Material.STONE).strength(-1.0F, 3_600_000.0F).sound(SoundType.STONE).noLootTable().isValidSpawn((s, l, p, e) -> false)));
    public static final Supplier<Item> OIL_SPRING_ITEM = Reg.blockItem("oil_spring", OIL_SPRING, BuildCraft.itemProperties());
    public static final Supplier<OilWellFeature> OIL_WELL = Platform.register(Registry.FEATURE_REGISTRY, "oil_well", OilWellFeature::new);

    public static final Supplier<EngineBlock> REDSTONE_ENGINE = Reg.block("redstone_engine",
            () -> new EngineBlock(engineProperties(SoundType.WOOD), BCEnergy.REDSTONE_ENGINE_TYPE));
    public static final Supplier<EngineBlock> STIRLING_ENGINE = Reg.block("stirling_engine",
            () -> new EngineBlock(engineProperties(SoundType.STONE), BCEnergy.STIRLING_ENGINE_TYPE));
    public static final Supplier<EngineBlock> COMBUSTION_ENGINE = Reg.block("combustion_engine",
            () -> new EngineBlock(engineProperties(SoundType.METAL), BCEnergy.COMBUSTION_ENGINE_TYPE));

    public static final Supplier<Item> REDSTONE_ENGINE_ITEM = Reg.blockItem("redstone_engine", REDSTONE_ENGINE, BuildCraft.itemProperties());
    public static final Supplier<Item> STIRLING_ENGINE_ITEM = Reg.blockItem("stirling_engine", STIRLING_ENGINE, BuildCraft.itemProperties());
    public static final Supplier<Item> COMBUSTION_ENGINE_ITEM = Reg.blockItem("combustion_engine", COMBUSTION_ENGINE, BuildCraft.itemProperties());

    public static final Supplier<BlockEntityType<RedstoneEngineBlockEntity>> REDSTONE_ENGINE_TYPE = Reg.blockEntity("redstone_engine",
            () -> BlockEntityType.Builder.of(RedstoneEngineBlockEntity::new, REDSTONE_ENGINE.get()).build(null));
    public static final Supplier<BlockEntityType<StirlingEngineBlockEntity>> STIRLING_ENGINE_TYPE = Reg.blockEntity("stirling_engine",
            () -> BlockEntityType.Builder.of(StirlingEngineBlockEntity::new, STIRLING_ENGINE.get()).build(null));
    public static final Supplier<BlockEntityType<CombustionEngineBlockEntity>> COMBUSTION_ENGINE_TYPE = Reg.blockEntity("combustion_engine",
            () -> BlockEntityType.Builder.of(CombustionEngineBlockEntity::new, COMBUSTION_ENGINE.get()).build(null));

    public static final Supplier<MenuType<StirlingEngineMenu>> STIRLING_ENGINE_MENU = Platform.menu("stirling_engine", StirlingEngineMenu::new);
    public static final Supplier<MenuType<CombustionEngineMenu>> COMBUSTION_ENGINE_MENU = Platform.menu("combustion_engine", CombustionEngineMenu::new);

    private BCEnergy() {}

    public static void init() {
        FuelRegistry.init();
    }

    private static BlockBehaviour.Properties engineProperties(SoundType sound) {
        return BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 10.0F).sound(sound).noOcclusion();
    }

    private static BCFluidFamily family(String name, int density, int viscosity, int boilPoint, int spread, int light, int dark) {
        BCFluidFamily family = new BCFluidFamily(name, density, viscosity, boilPoint, spread, light, dark);
        for (BCFluidDefinition definition : family.heats()) define(definition);
        FAMILIES.add(family);
        return family;
    }

    private static void define(BCFluidDefinition definition) {
        String name = definition.name;
        Platform.get().defineFluid(definition);
        Reg.fluid(name, () -> definition.source = Platform.get().fluid(definition, true));
        Reg.fluid("flowing_" + name, () -> definition.flowing = Platform.get().fluid(definition, false));
        definition.block = Reg.block(name, () -> Platform.get().liquidBlock(() -> definition.source,
                BlockBehaviour.Properties.of(Material.WATER).noCollission().strength(100.0F).noLootTable()));
        definition.bucket = Reg.item(name + "_bucket", () -> Platform.get().bucket(() -> definition.source,
                BuildCraft.itemProperties().craftRemainder(Items.BUCKET).stacksTo(1)));
    }
}
