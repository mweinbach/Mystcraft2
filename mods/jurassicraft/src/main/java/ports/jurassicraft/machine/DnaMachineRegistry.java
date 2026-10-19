package ports.jurassicraft.machine;

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

/** Registration for the four stages of DNA processing. */
public final class DnaMachineRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PortMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, PortMod.MOD_ID);
    public static final RegistryObject<Block> EXTRACTOR = block(DnaMachineKind.EXTRACTOR);
    public static final RegistryObject<Block> SEQUENCER = block(DnaMachineKind.SEQUENCER);
    public static final RegistryObject<Block> COMBINATOR = block(DnaMachineKind.COMBINATOR);
    public static final RegistryObject<Block> SYNTHESIZER = block(DnaMachineKind.SYNTHESIZER);
    public static final RegistryObject<BlockEntityType<DnaMachineBlockEntity>> ENTITY = ENTITIES.register("dna_machine", () ->
            BlockEntityType.Builder.of(DnaMachineBlockEntity::new, EXTRACTOR.get(), SEQUENCER.get(), COMBINATOR.get(), SYNTHESIZER.get()).build(null));
    public static final RegistryObject<MenuType<DnaMachineMenu>> MENU = MENUS.register("dna_machine", () -> IForgeMenuType.create(DnaMachineMenu::new));
    private DnaMachineRegistry() {}
    private static RegistryObject<Block> block(DnaMachineKind kind) {
        RegistryObject<Block> block = BLOCKS.register(kind.id, () -> new DnaMachineBlock(kind));
        ITEMS.register(kind.id, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); }
}
