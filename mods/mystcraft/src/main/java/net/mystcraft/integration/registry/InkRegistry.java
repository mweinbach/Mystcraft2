package net.mystcraft.integration.registry;
import java.util.function.Supplier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.FlowingFluid;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.platform.RegistryBridge;
/** One shared registration specification; native fluid type/render/capability adapters stay loader-owned. */
public final class InkRegistry {
    public static Supplier<FlowingFluid> BLACK_INK,FLOWING_BLACK_INK;
    public static Supplier<Block> BLACK_INK_BLOCK;
    public static Supplier<Item> BLACK_INK_BUCKET;
    public static void registerFluids(RegistryBridge bridge){BLACK_INK=bridge.registerInkFluid("black_ink",true);FLOWING_BLACK_INK=bridge.registerInkFluid("flowing_black_ink",false);}
    public static void registerBlock(RegistryBridge bridge){BLACK_INK_BLOCK=bridge.registerBlock("black_ink",bridge::createInkFluidBlock);}
    public static void registerItems(RegistryBridge bridge){BLACK_INK_BUCKET=bridge.registerItem("black_ink_bucket",()->bridge.createInkBucket(new Item.Properties().tab(MystcraftItems.TAB).stacksTo(1).craftRemainder(Items.BUCKET)));}
    private InkRegistry(){}
}
