package ports.jurassicraft.skull;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.platform.Platform;

/** The display is placed using either of the existing Tyrannosaurus skull items. */
public final class SkullRegistry {
    public static final Supplier<Block> DISPLAY=Platform.register(Registry.BLOCK_REGISTRY, "skull_display",SkullBlock::new);
    public static final Supplier<BlockEntityType<SkullBlockEntity>> ENTITY=Platform.register(Registry.BLOCK_ENTITY_TYPE_REGISTRY, "skull_display",() -> BlockEntityType.Builder.of(SkullBlockEntity::new,DISPLAY.get()).build(null));
    private SkullRegistry() {}
    public static void init() { }
}
