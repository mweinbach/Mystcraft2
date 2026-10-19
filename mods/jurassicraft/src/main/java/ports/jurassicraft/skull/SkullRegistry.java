package ports.jurassicraft.skull;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;

/** The display is placed using either of the existing Tyrannosaurus skull items. */
public final class SkullRegistry {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,PortMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,PortMod.MOD_ID);
    public static final RegistryObject<Block> DISPLAY=BLOCKS.register("skull_display",SkullBlock::new);
    public static final RegistryObject<BlockEntityType<SkullBlockEntity>> ENTITY=ENTITIES.register("skull_display",() -> BlockEntityType.Builder.of(SkullBlockEntity::new,DISPLAY.get()).build(null));
    private SkullRegistry() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); ENTITIES.register(bus); }
}
