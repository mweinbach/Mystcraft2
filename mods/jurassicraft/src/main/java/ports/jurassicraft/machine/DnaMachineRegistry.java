package ports.jurassicraft.machine;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
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
    public static final Supplier<BlockEntityType<DnaMachineBlockEntity>> ENTITY = Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "dna_machine", () ->
            BlockEntityType.Builder.of(DnaMachineBlockEntity::new, EXTRACTOR.get(), SEQUENCER.get(), COMBINATOR.get(), SYNTHESIZER.get()).build(null));
    public static final Supplier<MenuType<DnaMachineMenu>> MENU = Platform.register(Registry.MENU_REGISTRY, "dna_machine", () -> Platform.menuType(DnaMachineMenu::new));
    private DnaMachineRegistry() {}
    private static Supplier<Block> block(DnaMachineKind kind) {
        Supplier<Block> block = Platform.register(Registry.BLOCK_REGISTRY, kind.id, () -> new DnaMachineBlock(kind));
        Platform.register(Registry.ITEM_REGISTRY, kind.id, () -> new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)));
        return block;
    }
    public static void init() { }
}
