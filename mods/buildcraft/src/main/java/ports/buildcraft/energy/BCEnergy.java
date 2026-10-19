package ports.buildcraft.energy;

import java.util.List;
import java.util.function.Supplier;
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

/** Engines, oil-family fluids and their buckets (upstream BuildCraft Core engine plus BuildCraft Energy). */
public final class BCEnergy {
    // Upstream table: density, viscosity, quanta, light colour, dark colour (heat 0 only).
    public static final BCFluidDefinition OIL = define("oil", 900, 2000, 6, 0x50_50_50, 0x05_05_05);
    public static final BCFluidDefinition OIL_RESIDUE = define("oil_residue", 1200, 4000, 4, 0x10_0F_10, 0x42_10_42);
    public static final BCFluidDefinition FUEL_LIGHT = define("fuel_light", 400, 600, 8, 0xFF_FF_30, 0xE4_CF_00);
    public static final List<BCFluidDefinition> FLUIDS = List.of(OIL, OIL_RESIDUE, FUEL_LIGHT);

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

    private static BCFluidDefinition define(String name, int density, int viscosity, int quanta, int light, int dark) {
        BCFluidDefinition definition = new BCFluidDefinition(name, density, viscosity, quanta, light, dark);
        Platform.get().defineFluid(definition);
        Reg.fluid(name, () -> definition.source = Platform.get().fluid(definition, true));
        Reg.fluid("flowing_" + name, () -> definition.flowing = Platform.get().fluid(definition, false));
        definition.block = Reg.block(name, () -> Platform.get().liquidBlock(() -> definition.source,
                BlockBehaviour.Properties.of(Material.WATER).noCollission().strength(100.0F).noLootTable()));
        definition.bucket = Reg.item(name + "_bucket", () -> Platform.get().bucket(() -> definition.source,
                BuildCraft.itemProperties().craftRemainder(Items.BUCKET).stacksTo(1)));
        return definition;
    }
}
