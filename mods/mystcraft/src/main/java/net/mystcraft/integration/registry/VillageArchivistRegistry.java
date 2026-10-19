package net.mystcraft.integration.registry;

import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.mystcraft.integration.platform.RegistryBridge;
import net.mystcraft.minecraft.exploration.ArchivistHouseElement;

public final class VillageArchivistRegistry {
    public static Supplier<StructurePoolElementType<ArchivistHouseElement>> HOUSE;
    private VillageArchivistRegistry() {}
    public static void register(RegistryBridge bridge) {
        HOUSE = bridge.registerStructurePoolElementType("archivist_house", () -> () -> ArchivistHouseElement.CODEC);
    }
}
