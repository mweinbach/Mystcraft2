package ports.jurassicraft.fence;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** Low-security fence with connected wires, redstone power and shocks. */
public final class FenceRegistry {
    public static final Supplier<SoundEvent> SHOCK = sound("fence_shock");
    public static final Supplier<Block> BASE = block("low_security_fence_base", new FenceBaseBlock(properties(3.5F)));
    public static final Supplier<Block> POLE = block("low_security_fence_pole", new FencePoleBlock(properties(3.0F).noOcclusion()));
    public static final Supplier<Block> WIRE = block("low_security_fence_wire", new FenceWireBlock(properties(2.0F).noOcclusion()));

    private FenceRegistry() {}
    private static ResourceLocation id(String name) { return new ResourceLocation(PortMod.MOD_ID, name); }
    private static SoundType soundType() { return SoundType.METAL; }
    private static BlockBehaviour.Properties properties(float hardness) {
        return BlockBehaviour.Properties.of(Material.METAL).strength(hardness).sound(soundType()).requiresCorrectToolForDrops();
    }
    private static Supplier<SoundEvent> sound(String name) {
        ResourceLocation id = id(name);
        SoundEvent value = Registry.register(Registry.SOUND_EVENT, id, new SoundEvent(id));
        return () -> value;
    }
    private static Supplier<Block> block(String name, Block value) {
        Registry.register(Registry.BLOCK, id(name), value);
        Registry.register(Registry.ITEM, id(name), new BlockItem(value, new Item.Properties().tab(JurassicContent.TAB)));
        return () -> value;
    }
    public static void init() {}
}
