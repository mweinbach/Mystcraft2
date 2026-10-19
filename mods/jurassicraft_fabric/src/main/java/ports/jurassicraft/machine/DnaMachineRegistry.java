package ports.jurassicraft.machine;

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

/** Registration for the four stages of DNA processing. */
public final class DnaMachineRegistry {
    public static final Supplier<Block> EXTRACTOR = block(DnaMachineKind.EXTRACTOR);
    public static final Supplier<Block> SEQUENCER = block(DnaMachineKind.SEQUENCER);
    public static final Supplier<Block> COMBINATOR = block(DnaMachineKind.COMBINATOR);
    public static final Supplier<Block> SYNTHESIZER = block(DnaMachineKind.SYNTHESIZER);
    public static final Supplier<BlockEntityType<DnaMachineBlockEntity>> ENTITY = hold(Registry.register(
            Registry.BLOCK_ENTITY_TYPE, id("dna_machine"), BlockEntityType.Builder.of(DnaMachineBlockEntity::new,
                    EXTRACTOR.get(), SEQUENCER.get(), COMBINATOR.get(), SYNTHESIZER.get()).build(null)));
    public static final Supplier<MenuType<DnaMachineMenu>> MENU = hold(ScreenHandlerRegistry.registerExtended(id("dna_machine"), DnaMachineMenu::new));
    private DnaMachineRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static <T> Supplier<T> hold(T value) { return () -> value; }
    private static Supplier<Block> block(DnaMachineKind kind) {
        Block block = Registry.register(Registry.BLOCK, id(kind.id), new DnaMachineBlock(kind));
        Registry.register(Registry.ITEM, id(kind.id), new BlockItem(block, new Item.Properties().tab(JurassicContent.TAB)));
        return hold(block);
    }
    public static void init() {}
}
