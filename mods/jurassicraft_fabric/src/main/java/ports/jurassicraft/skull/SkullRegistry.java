package ports.jurassicraft.skull;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;

/** The display is placed using either of the existing Tyrannosaurus skull items. */
public final class SkullRegistry {
    private static Block display;
    private static BlockItem placer;
    private static BlockEntityType<SkullBlockEntity> entity;
    public static final Supplier<Block> DISPLAY = () -> display;
    public static final Supplier<BlockEntityType<SkullBlockEntity>> ENTITY = () -> entity;
    static BlockItem placer() { return placer; }
    private SkullRegistry() {}
    public static void register() {
        ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, "skull_display");
        display = Registry.register(Registry.BLOCK, id, new SkullBlock());
        // Items have intrusive holders in 1.19.2 and must be registered before
        // registry freeze. Only skull items call place() on this hidden helper.
        placer = Registry.register(Registry.ITEM, id, new BlockItem(display, new Item.Properties()) {
            @Override public InteractionResult useOn(UseOnContext context) { return InteractionResult.PASS; }
        });
        entity = Registry.register(Registry.BLOCK_ENTITY_TYPE, id, BlockEntityType.Builder.of(SkullBlockEntity::new, display).build(null));
    }
}
