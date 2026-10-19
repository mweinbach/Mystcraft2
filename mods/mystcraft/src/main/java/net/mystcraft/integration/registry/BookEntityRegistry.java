package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.mystcraft.integration.platform.RegistryBridge;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.mystcraft.minecraft.entity.LinkBookEntity;

public final class BookEntityRegistry {
    public static Supplier<EntityType<LinkBookEntity>> LINK_BOOK;

    private BookEntityRegistry() {}

    public static void register(RegistryBridge bridge) {
        LINK_BOOK = bridge.registerEntityType(
            "link_book", () -> EntityType.Builder.<LinkBookEntity>of(LinkBookEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.25F).clientTrackingRange(8).updateInterval(10).build("mystcraft:link_book"));
    }
}
