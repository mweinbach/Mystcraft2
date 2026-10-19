package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.fissure.StarFissureBlock;
import net.mystcraft.minecraft.fissure.StarFissureBlockEntity;

/** Natural escape feature; intentionally no craftable item or survival drop. */
public final class StarFissureRegistry {
    public static Supplier<Block> STAR_FISSURE;
    public static Supplier<BlockEntityType<StarFissureBlockEntity>> STAR_FISSURE_ENTITY;
    public static Supplier<SoundEvent> LINK_SOUND;
    private StarFissureRegistry() {}
    public static void registerBlocks(RegistryBridge bridge) {
        STAR_FISSURE = bridge.registerBlock("star_fissure", StarFissureBlock::new);
    }
    public static void register(RegistryBridge bridge) {
        STAR_FISSURE_ENTITY = bridge.registerBlockEntityType("star_fissure", () ->
                BlockEntityType.Builder.of(StarFissureBlockEntity::new, STAR_FISSURE.get()).build(null));
        LINK_SOUND = bridge.registerSoundEvent("linking.link-fissure", () ->
                new SoundEvent(new ResourceLocation("mystcraft", "linking.link-fissure")));
    }
}
