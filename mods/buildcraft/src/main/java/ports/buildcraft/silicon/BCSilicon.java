package ports.buildcraft.silicon;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;
import ports.buildcraft.platform.Reg;
import ports.buildcraft.silicon.gate.GateCopierItem;
import ports.buildcraft.silicon.gate.GateItem;
import ports.buildcraft.silicon.gate.GateMenu;
import ports.buildcraft.silicon.gate.GateVariant;
import ports.buildcraft.silicon.gate.PluggableGate;
import ports.buildcraft.silicon.plug.LensItem;
import ports.buildcraft.silicon.plug.LensPluggable;
import ports.buildcraft.silicon.plug.LensVariant;
import ports.buildcraft.silicon.plug.PluggableLightSensor;
import ports.buildcraft.silicon.plug.PluggablePulsar;
import ports.buildcraft.silicon.plug.PluggableTimer;
import ports.buildcraft.silicon.statement.BCSiliconStatements;
import ports.buildcraft.transport.pluggable.PluggableDefinition;
import ports.buildcraft.transport.pluggable.SimplePluggableItem;

/**
 * Upstream BuildCraft Silicon: the laser, the assembly, advanced crafting and integration tables it powers, the
 * five chipsets the assembly table makes, gates with the gate copier, lenses and filters, and the pipe pulsar, timer and light sensor.
 * Upstream's single
 * {@code redstone_chipset} item with five damage values is flattened into one item per chipset, its single NBT-keyed
 * gate item into one item per gate variant, and its 34-damage lens item into one item per lens or filter.
 */
public final class BCSilicon {
    public static final Supplier<LaserBlock> LASER = Reg.block("laser", () -> new LaserBlock(machine().noOcclusion()));
    public static final Supplier<LaserTableBlock> ASSEMBLY_TABLE = Reg.block("assembly_table",
            () -> new LaserTableBlock(machine().noOcclusion(), AssemblyTableBlockEntity::new, false));
    public static final Supplier<LaserTableBlock> ADVANCED_CRAFTING_TABLE = Reg.block("advanced_crafting_table",
            () -> new LaserTableBlock(machine().noOcclusion(), AdvancedCraftingTableBlockEntity::new, false));
    public static final Supplier<LaserTableBlock> INTEGRATION_TABLE = Reg.block("integration_table",
            () -> new LaserTableBlock(machine().noOcclusion(), IntegrationTableBlockEntity::new, true));

    public static final Supplier<Item> LASER_ITEM = Reg.blockItem("laser", LASER, BuildCraft.itemProperties());
    public static final Supplier<Item> ASSEMBLY_TABLE_ITEM = Reg.blockItem("assembly_table", ASSEMBLY_TABLE, BuildCraft.itemProperties());
    public static final Supplier<Item> ADVANCED_CRAFTING_TABLE_ITEM = Reg.blockItem("advanced_crafting_table", ADVANCED_CRAFTING_TABLE, BuildCraft.itemProperties());
    public static final Supplier<Item> INTEGRATION_TABLE_ITEM = Reg.blockItem("integration_table", INTEGRATION_TABLE, BuildCraft.itemProperties());

    public static final Supplier<Item> REDSTONE_CHIPSET = chipset("redstone_chipset");
    public static final Supplier<Item> IRON_CHIPSET = chipset("iron_chipset");
    public static final Supplier<Item> GOLD_CHIPSET = chipset("gold_chipset");
    public static final Supplier<Item> QUARTZ_CHIPSET = chipset("quartz_chipset");
    public static final Supplier<Item> DIAMOND_CHIPSET = chipset("diamond_chipset");

    public static final Supplier<BlockEntityType<LaserBlockEntity>> LASER_TYPE = Reg.blockEntity("laser",
            () -> BlockEntityType.Builder.of(LaserBlockEntity::new, LASER.get()).build(null));
    public static final Supplier<BlockEntityType<AssemblyTableBlockEntity>> ASSEMBLY_TABLE_TYPE = Reg.blockEntity("assembly_table",
            () -> BlockEntityType.Builder.of(AssemblyTableBlockEntity::new, ASSEMBLY_TABLE.get()).build(null));
    public static final Supplier<BlockEntityType<AdvancedCraftingTableBlockEntity>> ADVANCED_CRAFTING_TABLE_TYPE = Reg.blockEntity("advanced_crafting_table",
            () -> BlockEntityType.Builder.of(AdvancedCraftingTableBlockEntity::new, ADVANCED_CRAFTING_TABLE.get()).build(null));
    public static final Supplier<BlockEntityType<IntegrationTableBlockEntity>> INTEGRATION_TABLE_TYPE = Reg.blockEntity("integration_table",
            () -> BlockEntityType.Builder.of(IntegrationTableBlockEntity::new, INTEGRATION_TABLE.get()).build(null));

    public static final Supplier<MenuType<AssemblyTableMenu>> ASSEMBLY_TABLE_MENU = Platform.menu("assembly_table", AssemblyTableMenu::new);
    public static final Supplier<MenuType<AdvancedCraftingTableMenu>> ADVANCED_CRAFTING_TABLE_MENU =
            Platform.menu("advanced_crafting_table", AdvancedCraftingTableMenu::new);
    public static final Supplier<MenuType<IntegrationTableMenu>> INTEGRATION_TABLE_MENU = Platform.menu("integration_table", IntegrationTableMenu::new);

    public static final PluggableDefinition GATE = PluggableDefinition.register(BuildCraft.id("gate"), PluggableGate::new);
    public static final Map<GateVariant, Supplier<Item>> GATE_ITEMS = new LinkedHashMap<>();
    public static final Supplier<Item> GATE_COPIER = Reg.item("gate_copier", () -> new GateCopierItem(BuildCraft.itemProperties()));
    public static final Supplier<MenuType<GateMenu>> GATE_MENU = Platform.menu("gate", GateMenu::new);

    public static final PluggableDefinition LENS = PluggableDefinition.register(BuildCraft.id("lens"), LensPluggable::new);
    public static final Map<LensVariant, Supplier<Item>> LENS_ITEMS = new LinkedHashMap<>();
    public static final PluggableDefinition PULSAR = PluggableDefinition.register(BuildCraft.id("pulsar"), PluggablePulsar::new);
    public static final PluggableDefinition TIMER = PluggableDefinition.register(BuildCraft.id("timer"), PluggableTimer::new);
    public static final Supplier<Item> PULSAR_ITEM = Reg.item("plug_pulsar",
            () -> new SimplePluggableItem(BuildCraft.itemProperties(), PULSAR, SimplePluggableItem.BEHAVIOUR_ACCEPTS_REDSTONE_POWER));
    public static final Supplier<Item> TIMER_ITEM = Reg.item("plug_timer",
            () -> new SimplePluggableItem(BuildCraft.itemProperties(), TIMER, null));
    public static final PluggableDefinition LIGHT_SENSOR = PluggableDefinition.register(BuildCraft.id("daylight_sensor"), PluggableLightSensor::new);
    public static final Supplier<Item> LIGHT_SENSOR_ITEM = Reg.item("plug_light_sensor",
            () -> new SimplePluggableItem(BuildCraft.itemProperties(), LIGHT_SENSOR, null));

    static {
        for (GateVariant variant : GateVariant.VALUES) {
            GATE_ITEMS.put(variant, Reg.item("gate_" + variant.variantName(), () -> new GateItem(BuildCraft.itemProperties(), variant)));
        }
        for (LensVariant variant : LensVariant.VALUES) {
            LENS_ITEMS.put(variant, Reg.item(variant.itemName(), () -> new LensItem(BuildCraft.itemProperties(), variant)));
        }
    }

    public static final Supplier<RecipeType<AssemblyRecipe>> ASSEMBLY_TYPE = recipeType("assembly");
    public static final Supplier<RecipeType<IntegrationRecipe>> INTEGRATION_TYPE = recipeType("integration");
    public static final Supplier<RecipeSerializer<AssemblyRecipe>> ASSEMBLY_SERIALIZER =
            Platform.register(Registry.RECIPE_SERIALIZER_REGISTRY, "assembly", AssemblyRecipe.Serializer::new);
    public static final Supplier<RecipeSerializer<IntegrationRecipe>> INTEGRATION_SERIALIZER =
            Platform.register(Registry.RECIPE_SERIALIZER_REGISTRY, "integration", IntegrationRecipe.Serializer::new);

    private BCSilicon() {}

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of(Material.METAL).strength(5.0F, 10.0F).sound(SoundType.METAL);
    }

    /** The item of {@code variant}; variants no item makes, such as a modified clay gate from edited data, give the basic gate. */
    public static Item gateItem(GateVariant variant) {
        Supplier<Item> item = GATE_ITEMS.get(variant);
        return (item != null ? item : GATE_ITEMS.get(GateVariant.BASIC)).get();
    }

    public static Item lensItem(LensVariant variant) {
        return LENS_ITEMS.get(variant).get();
    }

    private static Supplier<Item> chipset(String name) {
        return Reg.item(name, () -> new Item(BuildCraft.itemProperties()));
    }

    private static <T extends Recipe<?>> Supplier<RecipeType<T>> recipeType(String name) {
        String id = BuildCraft.id(name).toString();
        return Platform.register(Registry.RECIPE_TYPE_REGISTRY, name, () -> new RecipeType<T>() {
            @Override
            public String toString() {
                return id;
            }
        });
    }

    public static void init() {
        BCSiliconStatements.init();
    }
}
