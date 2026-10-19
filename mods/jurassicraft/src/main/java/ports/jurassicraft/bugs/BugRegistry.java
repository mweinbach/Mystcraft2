package ports.jurassicraft.bugs;

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

public final class BugRegistry {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,PortMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(ForgeRegistries.MENU_TYPES,PortMod.MOD_ID);
    public static final RegistryObject<Block> CRATE=BLOCKS.register("bug_crate",BugCrateBlock::new);
    public static final RegistryObject<Block> PLANKTON_SWARM=BLOCKS.register("plankton_swarm",() -> new SwarmBlock(BugRegistry::plankton));
    public static final RegistryObject<Block> KRILL_SWARM=BLOCKS.register("krill_swarm",() -> new SwarmBlock(BugRegistry::krill));
    public static final RegistryObject<Item> CRATE_ITEM=ITEMS.register("bug_crate",() -> new BlockItem(CRATE.get(),properties()));
    public static final RegistryObject<Item> CRICKETS=ITEMS.register("crickets",() -> new Item(properties()));
    public static final RegistryObject<Item> COCKROACHES=ITEMS.register("cockroaches",() -> new Item(properties()));
    public static final RegistryObject<Item> MEALWORM_BEETLES=ITEMS.register("mealworm_beetles",() -> new Item(properties()));
    public static final RegistryObject<Item> FINE_NET=ITEMS.register("fine_net",() -> new FineNetItem(properties().durability(50)));
    public static final RegistryObject<Item> PLANKTON=ITEMS.register("plankton",() -> new SwarmItem(properties(),() -> PLANKTON_SWARM.get()));
    public static final RegistryObject<Item> KRILL=ITEMS.register("krill",() -> new SwarmItem(properties(),() -> KRILL_SWARM.get()));
    public static final RegistryObject<BlockEntityType<BugCrateBlockEntity>> ENTITY=ENTITIES.register("bug_crate",() -> BlockEntityType.Builder.of(BugCrateBlockEntity::new,CRATE.get()).build(null));
    public static final RegistryObject<MenuType<BugCrateMenu>> MENU=MENUS.register("bug_crate",() -> IForgeMenuType.create(BugCrateMenu::new));
    private BugRegistry() {}
    private static Item plankton() { return PLANKTON.get(); }
    private static Item krill() { return KRILL.get(); }
    private static Item.Properties properties() { return new Item.Properties().tab(JurassicContent.TAB); }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); }
}
