package ports.jurassicraft.village;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.machine.DnaMachineRegistry;

public final class ZombieVillageGameTests {
    private ZombieVillageGameTests() {}
    private static void require(GameTestHelper h, boolean condition, String message) { if (!condition) h.fail(message); }

    private static StructurePoolElement candidate(GameTestHelper h, String biome, String name) {
        var pools = h.getLevel().registryAccess().registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY);
        var pool = pools.get(new ResourceLocation("minecraft", "village/" + biome + "/zombie/houses"));
        require(h, pool != null, "missing abandoned village pool for " + biome);
        String id = "jurassicraft:village/" + biome + "/zombie/" + name;
        return pool.getShuffledTemplates(RandomSource.create(1)).stream()
                .filter(element -> element.toString().contains(id)).findFirst().orElseThrow();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void abandonedPoolsUseMatchingStreetAndIdempotentInjection(GameTestHelper h) {
        var pools = h.getLevel().registryAccess().registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY);
        var processors = h.getLevel().registryAccess().registryOrThrow(Registry.PROCESSOR_LIST_REGISTRY);
        for (String biome : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
            var pool = pools.get(new ResourceLocation("minecraft", "village/" + biome + "/zombie/houses"));
            require(h, pool != null, "missing abandoned village pool for " + biome);
            int size = pool.size();
            VillageLimitChecks.requireUpstreamDigsiteWeights(h, pool.getShuffledTemplates(RandomSource.create(1)),
                    "jurassicraft:village/" + biome + "/zombie/");
            for (String name : List.of("geneticist_house", "fossildigsite_1", "fossildigsite_2", "fossildigsite_3")) {
                var element = candidate(h, biome, name);
                var markers = element.getShuffledJigsawBlocks(h.getLevel().getStructureManager(), BlockPos.ZERO, Rotation.NONE, RandomSource.create(1));
                require(h, markers.size() == 1 && "minecraft:building_entrance".equals(markers.get(0).nbt.getString("name")), "missing entrance for " + biome + "/" + name);
                require(h, ("minecraft:village/" + biome + "/zombie/streets").equals(markers.get(0).nbt.getString("pool")), "incorrect zombie street pool for " + biome + "/" + name);
            }
            var decay = processors.get(new ResourceLocation("minecraft", "zombie_" + biome));
            require(h, decay != null && !decay.list().isEmpty(), "missing abandoned-village processors for " + biome);
            VillagePools.injectAbandoned(pool, biome, decay);
            require(h, pool.size() == size, "zombie pool injection must be idempotent");
            var normal = pools.get(new ResourceLocation("minecraft", "village/" + biome + "/houses"));
            require(h, normal != null && normal.getShuffledTemplates(RandomSource.create(1)).stream()
                    .noneMatch(element -> element.toString().contains("jurassicraft:village/" + biome + "/zombie/")), "zombie piece leaked into normal pool");
        }
        h.succeed();
    }

    private static void place(GameTestHelper h, String name, BlockPos local) {
        var level = h.getLevel();
        var origin = h.absolutePos(local);
        boolean placed = candidate(h, "plains", name).place(level.getStructureManager(), level, level.structureManager(),
                level.getChunkSource().getGenerator(), origin, origin, Rotation.NONE, BoundingBox.infinite(), RandomSource.create(42), false);
        require(h, placed, "could not place abandoned " + name);
    }

    private static void loot(GameTestHelper h, BlockPos pos, String name) {
        var blockEntity = h.getLevel().getBlockEntity(h.absolutePos(pos));
        require(h, blockEntity instanceof ChestBlockEntity && blockEntity.saveWithoutMetadata().getString("LootTable").equals("jurassicraft:structure/" + name), "missing abandoned village loot");
    }

    /** The pieces spawn persistent zombie villagers that would otherwise hunt villagers in concurrent tests. */
    private static void discardZombies(GameTestHelper h, BlockPos origin) {
        h.getEntities(EntityType.ZOMBIE_VILLAGER, origin, 24).forEach(Entity::discard);
    }

    @GameTest(template = "jurassicraft:park_structure_test")
    public static void abandonedGeneticistHouseSpawnsZombieProfessional(GameTestHelper h) {
        BlockPos origin = new BlockPos(4, 1, 4);
        try {
            place(h, "geneticist_house", origin);
            require(h, h.getBlockState(origin.offset(4, 2, 4)).is(DnaMachineRegistry.EXTRACTOR.get()), "geneticist workstation missing");
            loot(h, origin.offset(6, 1, 6), "geneticist_house");
            require(h, h.getEntities(EntityType.ZOMBIE_VILLAGER, origin.offset(3, 1, 3), 4).stream().anyMatch(v ->
                    v.getVillagerData().getProfession() == VillageRegistry.GENETICIST.get() && v.getVillagerXp() == 1 && v.isPersistenceRequired()), "geneticist zombie did not spawn with profession and retained experience");
            require(h, h.getEntities(EntityType.VILLAGER, origin.offset(3, 1, 3), 4).isEmpty(), "ordinary villager spawned in an abandoned house");
        } finally {
            discardZombies(h, origin);
        }
        h.succeed();
    }

    @GameTest(template = "jurassicraft:park_structure_test")
    public static void abandonedDigsiteKeepsFossilDisplayAndZombieProfessional(GameTestHelper h) {
        BlockPos origin = new BlockPos(4, 1, 4);
        try {
            place(h, "fossildigsite_3", origin);
            require(h, h.getBlockState(origin.offset(1, 12, 1)).is(CleaningStationRegistry.BLOCK.get()), "paleontologist workstation missing");
            loot(h, origin.offset(7, 2, 7), "fossil_digsite");
            require(h, h.getLevel().getBlockEntity(h.absolutePos(origin.offset(5, 1, 3))) instanceof AncientItemHoldingBlockEntity, "abandoned digsite tool display missing");
            require(h, h.getEntities(EntityType.ZOMBIE_VILLAGER, origin.offset(3, 12, 3), 4).stream().anyMatch(v ->
                    v.getVillagerData().getProfession() == VillageRegistry.PALEONTOLOGIST.get() && v.getVillagerXp() == 1 && v.isPersistenceRequired()), "paleontologist zombie did not spawn with profession and retained experience");
        } finally {
            discardZombies(h, origin);
        }
        h.succeed();
    }
}
