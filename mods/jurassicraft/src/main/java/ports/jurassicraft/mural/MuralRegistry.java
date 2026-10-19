package ports.jurassicraft.mural;

import net.minecraft.core.Registry;
import ports.jurassicraft.platform.Platform;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** The original wall mural and the original, inert tracking implant item. */
public final class MuralRegistry {
    public static final Supplier<EntityType<MuralEntity>> ENTITY = Platform.register(Registry.ENTITY_TYPE_REGISTRY, "mural",
            () -> EntityType.Builder.<MuralEntity>of(MuralEntity::new, MobCategory.MISC).sized(8, 4)
                    .clientTrackingRange(12).updateInterval(10).build(PortMod.MOD_ID + ":mural"));
    public static final Supplier<Item> MURAL = Platform.register(Registry.ITEM_REGISTRY, "mural", MuralItem::new);
    // Upstream registers this item but provides no use, recipe, or tracker system.
    public static final Supplier<Item> TRACKER = Platform.register(Registry.ITEM_REGISTRY, "tracker", () -> new Item(new Item.Properties().tab(JurassicContent.TAB)));
    private MuralRegistry() {}
    public static void init() { }
}
