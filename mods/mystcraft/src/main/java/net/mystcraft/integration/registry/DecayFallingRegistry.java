package net.mystcraft.integration.registry;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.instability.DecayFallingBlockEntity;
public final class DecayFallingRegistry {
    public static Supplier<EntityType<DecayFallingBlockEntity>> FALLING;
    private DecayFallingRegistry() {}
    public static void register(RegistryBridge bridge) {
        FALLING=bridge.registerEntityType("decay_falling",()->EntityType.Builder.<DecayFallingBlockEntity>of(DecayFallingBlockEntity::new,MobCategory.MISC)
                .sized(.98F,.98F).clientTrackingRange(10).updateInterval(20).build("mystcraft:decay_falling"));
    }
}
