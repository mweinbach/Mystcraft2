package net.mystcraft.minecraft.linking;
import java.util.UUID;
/** Loader ServerPlayer mixins persist this value and copy it on native respawn. */
public interface DimensionIdentityCarrier {
    UUID mystcraft$getDimensionIdentity();
    void mystcraft$setDimensionIdentity(UUID identity);
    default net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> mystcraft$getRespawnOrigin(){return null;}
    default void mystcraft$setRespawnOrigin(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> origin){}

}
