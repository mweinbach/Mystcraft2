package ports.buildcraft.core;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Reg;

/** Upstream BuildCraft Core items: gears, the wrench and fragile fluid shards. */
public final class BCCore {
    public static final Supplier<Item> GEAR_WOOD = gear("wood");
    public static final Supplier<Item> GEAR_STONE = gear("stone");
    public static final Supplier<Item> GEAR_IRON = gear("iron");
    public static final Supplier<Item> GEAR_GOLD = gear("gold");
    public static final Supplier<Item> GEAR_DIAMOND = gear("diamond");
    public static final Supplier<WrenchItem> WRENCH = Reg.item("wrench", () -> new WrenchItem(BuildCraft.itemProperties().stacksTo(1)));
    public static final Supplier<FragileFluidShardItem> FRAGILE_FLUID_SHARD = Reg.item("fragile_fluid_shard",
            () -> new FragileFluidShardItem(new Item.Properties().stacksTo(1)));

    private BCCore() {}

    public static void init() {}

    /** The conventional Fabric gear tag for {@code material}, using 1.19.2-era {@code c:<material>_gears} names. */
    public static TagKey<Item> commonGearTag(String material) {
        return TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("c", material + "_gears"));
    }

    /** The Forge gear tag for {@code material}. */
    public static TagKey<Item> forgeGearTag(String material) {
        return TagKey.create(Registry.ITEM_REGISTRY, new ResourceLocation("forge", "gears/" + material));
    }

    private static Supplier<Item> gear(String material) {
        return Reg.item("gear_" + material, () -> new Item(BuildCraft.itemProperties()));
    }
}
