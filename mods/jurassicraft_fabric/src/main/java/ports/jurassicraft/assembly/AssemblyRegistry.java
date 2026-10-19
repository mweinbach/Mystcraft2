package ports.jurassicraft.assembly;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.skull.FreshSkullItem;

/** Skeleton table, display and species-specific fresh bones. */
public final class AssemblyRegistry {
    private static Block table, display;
    private static Item tableItem, displayItem;
    private static BlockEntityType<SkeletonDisplayBlockEntity> displayEntity;
    private static MenuType<SkeletonAssemblyMenu> menu;
    public static final Supplier<Block> TABLE = () -> table;
    public static final Supplier<Block> DISPLAY = () -> display;
    public static final Supplier<Item> TABLE_ITEM = () -> tableItem;
    public static final Supplier<Item> DISPLAY_ITEM = () -> displayItem;
    public static final Supplier<BlockEntityType<SkeletonDisplayBlockEntity>> DISPLAY_ENTITY = () -> displayEntity;
    public static final Supplier<MenuType<SkeletonAssemblyMenu>> MENU = () -> menu;
    public static final Map<Species, Map<String, Supplier<Item>>> FRESH_BONES = new EnumMap<>(Species.class);

    private AssemblyRegistry() {}
    private static ResourceLocation id(String name) { return new ResourceLocation(PortMod.MOD_ID, name); }
    public static void register() {
        table = Registry.register(Registry.BLOCK, id("skeleton_assembly"), new SkeletonAssemblyBlock());
        display = Registry.register(Registry.BLOCK, id("skeleton_display"), new SkeletonDisplayBlock());
        tableItem = Registry.register(Registry.ITEM, id("skeleton_assembly"), new BlockItem(table, new Item.Properties().tab(JurassicContent.TAB)));
        displayItem = Registry.register(Registry.ITEM, id("skeleton_display"), new SkeletonDisplayItem(display, new Item.Properties().tab(JurassicContent.TAB)));
        displayEntity = Registry.register(Registry.BLOCK_ENTITY_TYPE, id("skeleton_display"),
                BlockEntityType.Builder.of(SkeletonDisplayBlockEntity::new, display).build(null));
        menu = ScreenHandlerRegistry.registerExtended(id("skeleton_assembly"), SkeletonAssemblyMenu::new);
        for (Species species : Species.values()) {
            Map<String, Supplier<Item>> bones = new LinkedHashMap<>();
            for (String bone : species.bones()) {
                Item item = Registry.register(Registry.ITEM, id(species.id() + "_fresh_" + bone),
                        species == Species.TYRANNOSAURUS && bone.equals("skull")
                                ? new FreshSkullItem(new Item.Properties().tab(JurassicContent.TAB))
                                : new FreshBoneItem(species, new Item.Properties().tab(JurassicContent.TAB)));
                bones.put(bone, () -> item);
            }
            FRESH_BONES.put(species, Map.copyOf(bones));
        }
    }
}
