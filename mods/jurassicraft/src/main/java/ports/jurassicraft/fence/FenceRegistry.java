package ports.jurassicraft.fence;

import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** The three low-security fence parts that were enabled upstream. */
public final class FenceRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, PortMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, PortMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, PortMod.MOD_ID);
    public static final RegistryObject<SoundEvent> SHOCK = SOUNDS.register("fence_shock",
            () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, "fence_shock")));
    public static final RegistryObject<Block> BASE = BLOCKS.register("low_security_fence_base", () -> new FenceBaseBlock(properties(3.5F)));
    public static final RegistryObject<Block> POLE = BLOCKS.register("low_security_fence_pole", () -> new FencePoleBlock(properties(3.0F).noOcclusion()));
    public static final RegistryObject<Block> WIRE = BLOCKS.register("low_security_fence_wire", () -> new FenceWireBlock(properties(2.0F).noOcclusion()));
    static {
        ITEMS.register("low_security_fence_base", () -> blockItem(BASE));
        ITEMS.register("low_security_fence_pole", () -> blockItem(POLE));
        ITEMS.register("low_security_fence_wire", () -> blockItem(WIRE));
    }
    private FenceRegistry() {}
    private static BlockBehaviour.Properties properties(float hardness) {
        return BlockBehaviour.Properties.of(Material.METAL).strength(hardness).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }
    private static Item blockItem(RegistryObject<Block> block) { return new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)); }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); SOUNDS.register(bus); }
}
