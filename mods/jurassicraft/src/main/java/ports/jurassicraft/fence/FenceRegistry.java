package ports.jurassicraft.fence;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** The three low-security fence parts that were enabled upstream. */
public final class FenceRegistry {
    public static final Supplier<SoundEvent> SHOCK = Platform.register(Registry.SOUND_EVENT_REGISTRY, "fence_shock",
            () -> new SoundEvent(new ResourceLocation(PortMod.MOD_ID, "fence_shock")));
    public static final Supplier<Block> BASE = Platform.register(Registry.BLOCK_REGISTRY, "low_security_fence_base", () -> new FenceBaseBlock(properties(3.5F)));
    public static final Supplier<Block> POLE = Platform.register(Registry.BLOCK_REGISTRY, "low_security_fence_pole", () -> new FencePoleBlock(properties(3.0F).noOcclusion()));
    public static final Supplier<Block> WIRE = Platform.register(Registry.BLOCK_REGISTRY, "low_security_fence_wire", () -> new FenceWireBlock(properties(2.0F).noOcclusion()));
    static {
        Platform.register(Registry.ITEM_REGISTRY, "low_security_fence_base", () -> blockItem(BASE));
        Platform.register(Registry.ITEM_REGISTRY, "low_security_fence_pole", () -> blockItem(POLE));
        Platform.register(Registry.ITEM_REGISTRY, "low_security_fence_wire", () -> blockItem(WIRE));
    }
    private FenceRegistry() {}
    private static BlockBehaviour.Properties properties(float hardness) {
        return BlockBehaviour.Properties.of(Material.METAL).strength(hardness).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }
    private static Item blockItem(Supplier<Block> block) { return new BlockItem(block.get(), new Item.Properties().tab(JurassicContent.TAB)); }
    public static void init() { }
}
