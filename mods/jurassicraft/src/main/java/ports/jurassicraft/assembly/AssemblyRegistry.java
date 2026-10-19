package ports.jurassicraft.assembly;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.content.Species;
import ports.jurassicraft.skull.FreshSkullItem;

public final class AssemblyRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, PortMod.MOD_ID);
    public static final RegistryObject<Block> TABLE = BLOCKS.register("skeleton_assembly", SkeletonAssemblyBlock::new);
    public static final RegistryObject<Block> DISPLAY = BLOCKS.register("skeleton_display", SkeletonDisplayBlock::new);
    public static final RegistryObject<Item> TABLE_ITEM = ITEMS.register("skeleton_assembly", () -> new BlockItem(TABLE.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final RegistryObject<Item> DISPLAY_ITEM = ITEMS.register("skeleton_display", () -> new SkeletonDisplayItem(DISPLAY.get(), new Item.Properties().tab(JurassicContent.TAB)));
    public static final Map<Species, Map<String, RegistryObject<Item>>> FRESH_BONES = new EnumMap<>(Species.class);
    static {
        for (Species species : Species.values()) {
            Map<String, RegistryObject<Item>> bones = new LinkedHashMap<>();
            for (String bone : species.bones()) {
                bones.put(bone, ITEMS.register(species.id() + "_fresh_" + bone,
                        () -> species == Species.TYRANNOSAURUS && bone.equals("skull")
                                ? new FreshSkullItem(new Item.Properties().tab(JurassicContent.TAB))
                                : new FreshBoneItem(species, new Item.Properties().tab(JurassicContent.TAB))));
            }
            FRESH_BONES.put(species, Map.copyOf(bones));
        }
    }
    public static final RegistryObject<BlockEntityType<SkeletonDisplayBlockEntity>> DISPLAY_ENTITY = BLOCK_ENTITIES.register("skeleton_display",
            () -> BlockEntityType.Builder.of(SkeletonDisplayBlockEntity::new, DISPLAY.get()).build(null));
    public static final RegistryObject<MenuType<SkeletonAssemblyMenu>> MENU = MENUS.register("skeleton_assembly", () -> IForgeMenuType.create(SkeletonAssemblyMenu::new));

    private AssemblyRegistry() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus); }
}
