package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.entity.MeteorEntity;

public final class MeteorRegistry {
    public static Supplier<EntityType<MeteorEntity>> METEOR;
    public static Supplier<net.minecraft.sounds.SoundEvent> ROAR;
    private MeteorRegistry() {}
    public static void register(RegistryBridge bridge) {
        ROAR = bridge.registerSoundEvent("entity.meteor.roar", () -> new net.minecraft.sounds.SoundEvent(
                new net.minecraft.resources.ResourceLocation("mystcraft", "entity.meteor.roar")));
        METEOR = bridge.registerEntityType("meteor", () -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                .sized(1, 1).clientTrackingRange(16).updateInterval(1).build("mystcraft:meteor"));
    }
    public static void spawn(ServerLevel level, double x, double y, double z) {
        MeteorEntity meteor = new MeteorEntity(METEOR.get(), level);
        meteor.setPos(x, y, z);
        meteor.setDeltaMovement(level.random.nextGaussian() * 0.25, level.random.nextFloat() * -2 - 2,
                level.random.nextGaussian() * 0.25);
        level.addFreshEntity(meteor);
    }
}
