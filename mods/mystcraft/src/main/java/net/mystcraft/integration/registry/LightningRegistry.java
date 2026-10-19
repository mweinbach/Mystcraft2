package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.instability.ClassicLightningBolt;

public final class LightningRegistry {
    public static Supplier<EntityType<ClassicLightningBolt>> LIGHTNING;
    private LightningRegistry(){}
    public static void register(RegistryBridge bridge){LIGHTNING=bridge.registerEntityType("lightning",()->EntityType.Builder.<ClassicLightningBolt>of(ClassicLightningBolt::new,MobCategory.MISC)
            .sized(0,0).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE).build("mystcraft:lightning"));}
    public static ClassicLightningBolt spawn(ServerLevel level,Vec3 position,float red,float green,float blue){
        ClassicLightningBolt bolt=new ClassicLightningBolt(LIGHTNING.get(),level);bolt.moveTo(position);bolt.color(red,green,blue);
        if(!level.addFreshEntity(bolt)){bolt.discard();return null;}return bolt;
    }
}
