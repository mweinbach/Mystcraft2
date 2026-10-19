package ports.jurassicraft.tourrail;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class TourRailRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PortMod.MOD_ID);
    public static final RegistryObject<Block> RAIL = rail("tour_rail", 0.125, 0xffffff);
    public static final RegistryObject<Block> SLOW = rail("tour_rail_slow", 0.0625, 0xa80000);
    public static final RegistryObject<Block> MEDIUM = rail("tour_rail_medium", 0.125, 0xff7600);
    public static final RegistryObject<Block> FAST = rail("tour_rail_fast", 0.25, 0x00a800);
    public static final RegistryObject<BlockEntityType<TourRailBlockEntity>> RENDER_ENTITY = BLOCK_ENTITIES.register("tour_rail",
            () -> BlockEntityType.Builder.of(TourRailBlockEntity::new, RAIL.get(), SLOW.get(), MEDIUM.get(), FAST.get()).build(null));
    private TourRailRegistry() {}
    private static RegistryObject<Block> rail(String name, double speed, int color) {
        RegistryObject<Block> block = BLOCKS.register(name, () -> new TourRailBlock(speed, color));
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); }
}
