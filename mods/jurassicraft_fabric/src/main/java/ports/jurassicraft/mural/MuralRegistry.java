package ports.jurassicraft.mural;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

public final class MuralRegistry {
    private static EntityType<MuralEntity> entity;
    private static Item mural, tracker;
    public static final Supplier<EntityType<MuralEntity>> ENTITY = () -> entity;
    public static final Supplier<Item> MURAL = () -> mural;
    public static final Supplier<Item> TRACKER = () -> tracker;
    private MuralRegistry() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }
    public static void register() {
        entity = Registry.register(Registry.ENTITY_TYPE, id("mural"), EntityType.Builder.<MuralEntity>of(MuralEntity::new, MobCategory.MISC)
                .sized(8, 4).clientTrackingRange(12).updateInterval(10).build(PortMod.MOD_ID + ":mural"));
        mural = Registry.register(Registry.ITEM, id("mural"), new MuralItem());
        tracker = Registry.register(Registry.ITEM, id("tracker"), new Item(new Item.Properties().tab(JurassicContent.TAB)));
    }
}
