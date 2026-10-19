package ports.jurassicraft.bugs;

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

public final class BugRegistry {
    public static final Supplier<Block> CRATE=block("bug_crate",new BugCrateBlock());
    public static final Supplier<Block> PLANKTON_SWARM=block("plankton_swarm",new SwarmBlock(BugRegistry::plankton));
    public static final Supplier<Block> KRILL_SWARM=block("krill_swarm",new SwarmBlock(BugRegistry::krill));
    public static final Supplier<Item> CRATE_ITEM=item("bug_crate",new BlockItem(CRATE.get(),properties()));
    public static final Supplier<Item> CRICKETS=item("crickets",new Item(properties()));
    public static final Supplier<Item> COCKROACHES=item("cockroaches",new Item(properties()));
    public static final Supplier<Item> MEALWORM_BEETLES=item("mealworm_beetles",new Item(properties()));
    public static final Supplier<Item> FINE_NET=item("fine_net",new FineNetItem(properties().durability(50)));
    public static final Supplier<Item> PLANKTON=item("plankton",new SwarmItem(properties(),PLANKTON_SWARM::get));
    public static final Supplier<Item> KRILL=item("krill",new SwarmItem(properties(),KRILL_SWARM::get));
    public static final Supplier<BlockEntityType<BugCrateBlockEntity>> ENTITY=entity("bug_crate",BlockEntityType.Builder.of(BugCrateBlockEntity::new,CRATE.get()).build(null));
    public static final Supplier<MenuType<BugCrateMenu>> MENU=menu();
    private static boolean initialized;
    private BugRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID,path); }
    private static Supplier<Block> block(String name,Block value) { Registry.register(Registry.BLOCK,id(name),value); return () -> value; }
    private static Supplier<Item> item(String name,Item value) { Registry.register(Registry.ITEM,id(name),value); return () -> value; }
    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> Supplier<BlockEntityType<T>> entity(String name,BlockEntityType<T> value) { Registry.register(Registry.BLOCK_ENTITY_TYPE,id(name),value); return () -> value; }
    private static Supplier<MenuType<BugCrateMenu>> menu() { MenuType<BugCrateMenu> value=ScreenHandlerRegistry.registerSimple(id("bug_crate"),BugCrateMenu::new); return () -> value; }
    private static Item plankton() { return PLANKTON.get(); }
    private static Item krill() { return KRILL.get(); }
    private static Item.Properties properties() { return new Item.Properties().tab(JurassicContent.TAB); }
    public static void init() { if (!initialized) { initialized=true; BugHarvest.init(); } }
}
