package ports.buildcraft.silicon;

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

/**
 * Upstream BuildCraft Silicon outside its gates: the laser, the assembly, advanced crafting and integration tables
 * it powers, and the five chipsets the assembly table makes. Upstream's single {@code redstone_chipset} item with
 * five damage values is flattened into one item per chipset.
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

    public static void init() {}
}
