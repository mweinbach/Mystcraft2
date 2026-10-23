package ports.jurassicraft.village;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.cleaning.CleaningStationRegistry;
import ports.jurassicraft.content.JurassicContent;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.machine.DnaMachineRegistry;

public final class VillageGameTests {
    private VillageGameTests() {}
    private static void require(GameTestHelper h, boolean condition, String message) { if (!condition) h.fail(message); }
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void jobsUseAllWorkstationStates(GameTestHelper h) {
        for (var entry : List.of(java.util.Map.entry(DnaMachineRegistry.EXTRACTOR.get(), VillageRegistry.GENETICIST_POI.get()),
                java.util.Map.entry(CleaningStationRegistry.BLOCK.get(), VillageRegistry.PALEONTOLOGIST_POI.get()))) {
            for (var state : entry.getKey().getStateDefinition().getPossibleStates()) {
                var type = PoiTypes.forState(state);
                require(h, type.isPresent() && type.get().value() == entry.getValue(), "workstation state lacks its POI: " + state);
            }
        }
        var trades = TestPlatform.get().trades(VillageRegistry.GENETICIST.get());
        require(h, trades.get(1) != null && trades.get(1).size() == 2 && trades.get(2) != null && trades.get(2).size() == 1
                , "geneticist trade levels differ");
        trades = TestPlatform.get().trades(VillageRegistry.PALEONTOLOGIST.get());
        require(h, trades.get(1) != null && trades.get(1).size() == 1 && trades.get(2) != null && trades.get(2).size() == 1
                && trades.get(3) != null && trades.get(3).size() == 2, "paleontologist trade levels differ");
        h.succeed();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void tradesKeepValidFossilSpeciesAndPrices(GameTestHelper h) {
        Set<Item> fossils = new HashSet<>();
        JurassicContent.BONES.values().forEach(bones -> bones.values().forEach(item -> fossils.add(item.get())));
        Set<Item> offeredFossils = new HashSet<>();
        for (int i = 0; i < 3000; i++) {
            var common = VillageTrades.geneticistCommon(RandomSource.create(i));
            var advanced = VillageTrades.geneticistAdvanced(RandomSource.create(i));
            var paleo = VillageTrades.paleontologistCommon(RandomSource.create(i));
            var special = VillageTrades.paleontologistSpecial(RandomSource.create(i));
            for (var offer : List.of(common, advanced, paleo, special)) {
                require(h, !offer.getBaseCostA().isEmpty() && !offer.getResult().isEmpty(), "empty trade");
                require(h, offer.getBaseCostA().getCount() <= offer.getBaseCostA().getMaxStackSize(), "trade price exceeds stack size");
                require(h, offer.getResult().getCount() <= offer.getResult().getMaxStackSize(), "trade result exceeds stack size");
            }
            if (special.getBaseCostA().is(Items.EMERALD)) {
                require(h, fossils.contains(special.getResult().getItem()), "sold fossil has no valid species");
                offeredFossils.add(special.getResult().getItem());
            } else require(h, fossils.contains(special.getBaseCostA().getItem()) && special.getResult().is(Items.EMERALD), "invalid fossil purchase");
        }
        require(h, offeredFossils.size() > 20, "specialist did not offer a range of species and bones");
        h.succeed();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void villageLimitsMatchUpstream(GameTestHelper h) {
        VillageLimitChecks.generatedVillageRespectsCaps(h);
        h.succeed();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void vanillaPoolsContainJigsawBuildings(GameTestHelper h) {
        var pools = h.getLevel().registryAccess().registryOrThrow(Registry.TEMPLATE_POOL_REGISTRY);
        for (String biome : List.of("plains", "desert", "savanna", "snowy", "taiga")) {
            var pool = pools.get(new ResourceLocation("minecraft", "village/" + biome + "/houses"));
            require(h, pool != null, "missing vanilla village pool for " + biome);
            int size = pool.size();
            var candidates = pool.getShuffledTemplates(RandomSource.create(1));
            VillageLimitChecks.requireUpstreamDigsiteWeights(h, candidates, "jurassicraft:village/" + biome + "/");
            for (String name : List.of("geneticist_house", "fossildigsite_1", "fossildigsite_2", "fossildigsite_3")) {
                String location = "jurassicraft:village/" + biome + "/" + name;
                var candidate = candidates.stream().filter(element -> element.toString().contains(location)).findFirst();
                require(h, candidate.isPresent(), "missing pool element " + location);
                var markers = candidate.orElseThrow().getShuffledJigsawBlocks(h.getLevel().getStructureManager(), BlockPos.ZERO, Rotation.NONE, RandomSource.create(1));
                require(h, markers.size() == 1 && "minecraft:building_entrance".equals(markers.get(0).nbt.getString("name")), "missing street connector " + location);
            }
            VillagePools.inject(pool, biome);
            require(h, pool.size() == size, "pool injection must be idempotent");
        }
        h.succeed();
    }

    private static void place(GameTestHelper h, String id, BlockPos local) {
        var level = h.getLevel();
        BlockPos origin = h.absolutePos(local);
        var settings = new StructurePlaceSettings().addProcessor(new VillageProcessor()).addProcessor(JigsawReplacementProcessor.INSTANCE);
        boolean success = level.getStructureManager().getOrCreate(id(id)).placeInWorld(level, origin, origin, settings, RandomSource.create(42), Block.UPDATE_ALL);
        require(h, success, "could not place " + id);
    }
    private static void loot(GameTestHelper h, BlockPos pos, String table) {
        var blockEntity = h.getLevel().getBlockEntity(h.absolutePos(pos));
        require(h, blockEntity instanceof ChestBlockEntity, "missing village chest at " + pos);
        require(h, blockEntity.saveWithoutMetadata().getString("LootTable").equals("jurassicraft:structure/" + table), "wrong village loot table");
    }
    @GameTest(template = "jurassicraft:park_structure_test")
    public static void geneticistHouseHasJobsiteVillagerAndLoot(GameTestHelper h) {
        BlockPos origin = new BlockPos(4,1,4);
        place(h, "village/plains/geneticist_house", origin);
        require(h, h.getBlockState(origin.offset(4,2,4)).is(DnaMachineRegistry.EXTRACTOR.get()), "geneticist workstation missing");
        loot(h, origin.offset(6,1,6), "geneticist_house");
        loot(h, origin.offset(6,1,7), "geneticist_house");
        require(h, h.getEntities(EntityType.VILLAGER, origin.offset(3,1,3), 4).stream()
                .anyMatch(v -> v.getVillagerData().getProfession() == VillageRegistry.GENETICIST.get() && v.getVillagerXp() == 1), "geneticist did not spawn");
        h.succeed();
    }
    @GameTest(template = "jurassicraft:park_structure_test")
    public static void digsiteHasFossilsLootAndDisplayedTool(GameTestHelper h) {
        BlockPos origin = new BlockPos(4,1,4);
        place(h, "village/plains/fossildigsite_3", origin);
        require(h, h.getBlockState(origin.offset(1,12,1)).is(CleaningStationRegistry.BLOCK.get()), "paleontologist workstation missing");
        loot(h, origin.offset(7,2,7), "fossil_digsite");
        BlockPos tool = origin.offset(5,1,3);
        var blockEntity = h.getLevel().getBlockEntity(h.absolutePos(tool));
        require(h, blockEntity instanceof AncientItemHoldingBlockEntity, "digsite tool display missing");
        var holder = (AncientItemHoldingBlockEntity) blockEntity;
        require(h, (holder.item().is(Items.IRON_PICKAXE) || holder.item().is(Items.IRON_SHOVEL))
                && holder.item().getDamageValue() >= 20 && holder.item().getDamageValue() <= 248 && holder.yOffset() == .5F,
                "wrong displayed tool or legacy offset");
        var before = h.getEntities(EntityType.ITEM, tool, 2).stream().map(item -> item.getUUID()).collect(java.util.stream.Collectors.toSet());
        h.getBlockState(tool).getBlock().playerWillDestroy(h.getLevel(), h.absolutePos(tool), h.getBlockState(tool), h.makeMockPlayer());
        require(h, h.getEntities(EntityType.ITEM, tool, 2).stream().anyMatch(item -> !before.contains(item.getUUID()) && item.getItem().is(holder.item().getItem())),
                "digging up the display did not drop its tool");
        require(h, h.getEntities(EntityType.VILLAGER, origin.offset(3,12,3), 4).stream()
                .anyMatch(v -> v.getVillagerData().getProfession() == VillageRegistry.PALEONTOLOGIST.get() && v.getVillagerXp() == 1), "paleontologist did not spawn");
        h.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void registeredStructureDatapackLoads(GameTestHelper h) {
        var structures = h.getLevel().registryAccess().registryOrThrow(Registry.STRUCTURE_REGISTRY);
        var sets = h.getLevel().registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY);
        for (String name : List.of("visitor_centre", "raptor_paddock")) {
            require(h, structures.get(id(name)) != null, "missing structure codec for " + name);
            require(h, sets.get(id(name)) != null, "missing structure set codec for " + name);
            require(h, h.getLevel().getStructureManager().get(id(name)).isPresent(), "missing structure template " + name);
        }
        h.succeed();
    }
}
