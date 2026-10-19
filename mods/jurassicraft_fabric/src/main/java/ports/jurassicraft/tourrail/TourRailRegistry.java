package ports.jurassicraft.tourrail;

import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class TourRailRegistry {
    public static final class Entry<T> implements Supplier<T> {
        private T value;
        private void set(T value) { this.value = Objects.requireNonNull(value); }
        @Override public T get() { return Objects.requireNonNull(value, "Tour rail registry not initialized"); }
    }
    public static final Entry<Block> RAIL = new Entry<>(), SLOW = new Entry<>(), MEDIUM = new Entry<>(), FAST = new Entry<>();
    public static final Entry<BlockEntityType<TourRailBlockEntity>> RENDER_ENTITY = new Entry<>();
    private TourRailRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    private static void rail(Entry<Block> entry, String name, double speed, int color) {
        Block block = Registry.register(Registry.BLOCK, id(name), new TourRailBlock(speed, color));
        entry.set(block);
        Registry.register(Registry.ITEM, id(name), new BlockItem(block, new Item.Properties().tab(JurassicContent.TAB)));
    }
    public static void init() {
        rail(RAIL, "tour_rail", .125, 0xffffff);
        rail(SLOW, "tour_rail_slow", .0625, 0xa80000);
        rail(MEDIUM, "tour_rail_medium", .125, 0xff7600);
        rail(FAST, "tour_rail_fast", .25, 0x00a800);
        RENDER_ENTITY.set(Registry.register(Registry.BLOCK_ENTITY_TYPE, id("tour_rail"),
                FabricBlockEntityTypeBuilder.create(TourRailBlockEntity::new, RAIL.get(), SLOW.get(), MEDIUM.get(), FAST.get()).build()));
    }
}
