package ports.jurassicraft.assembly;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.skull.FreshSkullItem;

public final class AssemblyRegistry {
    public static final Supplier<Block> TABLE = Platform.register(Registry.BLOCK_REGISTRY, "skeleton_assembly", SkeletonAssemblyBlock::new);
    public static final Supplier<Block> DISPLAY = Platform.register(Registry.BLOCK_REGISTRY, "skeleton_display", SkeletonDisplayBlock::new);
    public static final Supplier<Item> TABLE_ITEM = Platform.register(Registry.ITEM_REGISTRY, "skeleton_assembly", () -> new BlockItem(TABLE.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Supplier<Item> DISPLAY_ITEM = Platform.register(Registry.ITEM_REGISTRY, "skeleton_display", () -> new SkeletonDisplayItem(DISPLAY.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Map<Species, Map<String, Supplier<Item>>> FRESH_BONES = new EnumMap<>(Species.class);
    static {
        for (Species species : Species.values()) {
            Map<String, Supplier<Item>> bones = new LinkedHashMap<>();
            for (String bone : species.bones()) {
                bones.put(bone, Platform.register(Registry.ITEM_REGISTRY, species.id() + "_fresh_" + bone,
                        () -> species == Species.TYRANNOSAURUS && bone.equals("skull")
                                ? new FreshSkullItem(new Item.Properties().tab(JurassicContent.TAB))
                                : new FreshBoneItem(species, new Item.Properties().tab(JurassicContent.TAB))));
            }
            FRESH_BONES.put(species, Map.copyOf(bones));
        }
    }
    public static final Supplier<BlockEntityType<SkeletonDisplayBlockEntity>> DISPLAY_ENTITY = Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "skeleton_display",
            () -> BlockEntityType.Builder.of(SkeletonDisplayBlockEntity::new, DISPLAY.get()).build(null));
    public static final Supplier<MenuType<SkeletonAssemblyMenu>> MENU = Platform.register(Registry.MENU_REGISTRY, "skeleton_assembly", () -> Platform.menuType(SkeletonAssemblyMenu::new));

    private AssemblyRegistry() {}
    public static void init() { }
}
