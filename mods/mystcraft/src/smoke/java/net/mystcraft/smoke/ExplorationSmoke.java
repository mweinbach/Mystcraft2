package net.mystcraft.smoke;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.registry.ExplorationRegistry;
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.minecraft.exploration.ArchivistShop;
import net.mystcraft.minecraft.exploration.ClassicCrystalFeature;
import net.mystcraft.minecraft.exploration.ClassicLibraryFeature;
import net.mystcraft.minecraft.exploration.ExplorationPages;
import net.mystcraft.minecraft.exploration.MystLecternBlockEntity;
import net.mystcraft.minecraft.writing.PageData;
import net.mystcraft.minecraft.writing.SymbolRegistry;

/** Real registry/biome placement, level-block, loot and merchant assertions; test-only mod. */
public final class ExplorationSmoke {
    private ExplorationSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        List<String> passed = new ArrayList<>();
        var placed = server.registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        var libraryKey = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation("mystcraft", "small_library"));
        var crystalKey = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation("mystcraft", "crystal_formation"));
        var library = placed.getHolderOrThrow(libraryKey).value();
        var crystal = placed.getHolderOrThrow(crystalKey).value();
        int biomes = 0;
        for (var biome : server.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)) {
            require(biome.getGenerationSettings().hasFeature(library), "Biome missing library placed feature");
            require(biome.getGenerationSettings().hasFeature(crystal), "Biome missing crystal placed feature");
            biomes++;
        }
        require(biomes > 0, "No registered biomes tested");
        passed.add("exploration: both configured/placed features are present in all " + biomes + " registered biome generation settings, including modded biomes");
        require(!ExplorationPages.available(level.registryAccess()).isEmpty(), "No implemented symbol pages available to survival loot");
        for (int i = 0; i < 100; i++) {
            ItemStack page = ExplorationPages.sample(level.registryAccess(), RandomSource.create(i), false);
            require(!page.isEmpty() && ExplorationPages.rank(page, level.registryAccess()) >= 0, "Loot produced unusable catalog-only page");
        }
        LootContext lootContext = new LootContext.Builder(level).withRandom(RandomSource.create(771))
                .withParameter(LootContextParams.ORIGIN, new Vec3(100402.5, 280, 100402.5)).create(LootContextParamSets.CHEST);
        var loot = LoadedTreasureFixture.load(server).table().getRandomItems(lootContext);
        long pageEntries = loot.stream().filter(stack -> PageData.symbol(stack).isPresent()).count();
        require(loot.size() >= 4 && loot.size() <= 8, "Library table must produce four to eight mixed treasure rolls");
        require(loot.stream().allMatch(stack -> PageData.symbol(stack).isEmpty() || ExplorationPages.rank(stack, level.registryAccess()) >= 0), "Treasure produced invalid page payload");
        passed.add("exploration: actual loaded chest loot table and custom function generate mixed treasure with implemented pages and source rarity/stack rules");

        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> previousEntities = new LinkedHashMap<>();
        BlockPos origin = new BlockPos(100402, 280, 100402);
        try {
            rememberBox(level, origin.offset(0,-1,0), origin.offset(10,10,10), previous, previousEntities);
            for (int x = 0; x < 11; x++) for (int z = 0; z < 11; z++) level.setBlock(origin.offset(x,-1,z), Blocks.STONE.defaultBlockState(), 2);
            ClassicLibraryFeature feature = (ClassicLibraryFeature) ExplorationRegistry.SMALL_LIBRARY.get();
            require(feature.generateAt(level, origin, RandomSource.create(4096)), "Controlled real library placement failed");
            int shelves = 0, displays = 0, chests = 0;
            MystLecternBlockEntity first = null;
            for (int x = 0; x < 11; x++) for (int y = 0; y <= 10; y++) for (int z = 0; z < 11; z++) {
                BlockPos pos = origin.offset(x,y,z);
                if (level.getBlockState(pos).is(Blocks.BOOKSHELF)) shelves++;
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity instanceof MystLecternBlockEntity lectern) {
                    displays++; first = lectern;
                    require(!lectern.getBook().isEmpty() && PageData.symbol(lectern.getBook()).isPresent(), "Library lectern missing recoverable symbol page");
                }
                if (entity instanceof ChestBlockEntity chest) { chests++; chest.unpackLootTable(null); }
            }
            require(shelves == 47 && displays == 5 && chests == 1, "Source library geometry/chest/five real lecterns mismatch");
            require(first != null, "No lectern fixture");
            ItemStack expected = first.getBook().copy();
            BlockEntity firstEntity = first;
            CompoundTag tag = firstEntity.saveWithFullMetadata();
            BlockEntity restored = new MystLecternBlockEntity(firstEntity.getBlockPos(), firstEntity.getBlockState());
            restored.load(tag);
            require(ItemStack.matches(((MystLecternBlockEntity) restored).getBook(), expected), "Lectern save/load changed page payload");
            ItemStack recovered = first.takeBook();
            require(ItemStack.matches(recovered, expected) && first.getBook().isEmpty() && first.takeBook().isEmpty(), "Lectern recovery duplicated/lost page");
            passed.add("exploration: exact library blueprint creates 47 bookshelves, one actual loot chest and five page-bearing lecterns; page persistence/recovery is lossless");

            BlockPos root = new BlockPos(100450, 280, 100450);
            rememberBox(level, root.offset(-13,-2,-5), root.offset(13,14,5), previous, previousEntities);
            ClassicCrystalFeature crystalFeature = (ClassicCrystalFeature) ExplorationRegistry.CRYSTAL_FORMATION.get();
            net.minecraft.world.level.levelgen.feature.Feature<NoneFeatureConfiguration> vanillaFeature = crystalFeature;
            require(!vanillaFeature.place(new FeaturePlaceContext<>(Optional.empty(), level, level.getChunkSource().getGenerator(),
                    RandomSource.create(22), root, NoneFeatureConfiguration.INSTANCE)), "Crystal population unexpectedly ran in ordinary Overworld");
            require(crystalFeature.generateAt(level, root, RandomSource.create(22)), "Controlled crystal line placement failed");
            int count = 0;
            for (BlockPos pos : BlockPos.betweenClosed(root.offset(-13,-2,-5), root.offset(13,14,5))) {
                if (level.getBlockState(pos).is(PortalRegistry.CRYSTAL.get())) count++;
            }
            require(count >= 30 && count <= 300, "Crystal formation geometry outside source line/cross-thickness bounds");
            passed.add("exploration: real source-shaped crystal lines generate mineable crystal blocks; ordinary Overworld population is rejected");
        } finally {
            for (BlockPos pos : previous.keySet()) {
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity instanceof MystLecternBlockEntity lectern) lectern.takeBook();
                if (entity instanceof ChestBlockEntity chest) chest.clearContent();
            }
            for (var entry : previous.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 2);
            for (var entry : previousEntities.entrySet()) {
                BlockEntity entity = level.getBlockEntity(entry.getKey());
                if (entity != null) entity.load(entry.getValue());
            }
        }
        for (BlockState state : ExplorationRegistry.LECTERN.get().getStateDefinition().getPossibleStates()) {
            require(PoiTypes.forState(state).orElseThrow().value() == ExplorationRegistry.ARCHIVIST_POI.get(), "Lectern POI states not registered");
        }
        Villager villager = new Villager(EntityType.VILLAGER, level);
        villager.setVillagerData(villager.getVillagerData().setProfession(ExplorationRegistry.ARCHIVIST.get()));
        ArchivistShop.ensureOffers(villager);
        require(villager.getOffers().size() == 4, "Archivist must expose three classic rank offers and one booster offer");
        for (var offer : villager.getOffers()) {
            if (offer.getResult().is(net.mystcraft.integration.MystcraftItems.BOOSTER.get())) {
                require(offer.getBaseCostA().getCount() == 20 && offer.getMaxUses() == 5,
                        "Archivist booster must cost twenty emeralds with five initial stock");
                continue;
            }
            int rank = ExplorationPages.rank(offer.getResult(), level.registryAccess());
            require(rank >= 1 && rank <= 3 && offer.getBaseCostA().getCount() == 4 * (1 + rank)
                    && offer.getMaxUses() == 3, "Archivist price/rank/stock differs from active classic shop");
        }
        villager.getOffers().get(0).increaseUses();
        CompoundTag merchantTag = new CompoundTag(); villager.saveWithoutId(merchantTag);
        Villager reloaded = new Villager(EntityType.VILLAGER, level); reloaded.load(merchantTag);
        ArchivistShop.ensureOffers(reloaded);
        require(reloaded.getOffers().size() == 4 && reloaded.getOffers().get(0).getUses() == 1, "Archivist reload refreshed stock or duplicated offers");
        passed.add("exploration: archivist has rank1/2/3 pages at8/12/16 emeralds stock3 and booster at20 emeralds stock5; uses survive NBT reload");
        return passed;
    }
    private static void rememberBox(ServerLevel level, BlockPos from, BlockPos to,
                                    Map<BlockPos, BlockState> previous, Map<BlockPos, CompoundTag> entities) {
        for (BlockPos mutable : BlockPos.betweenClosed(from, to)) {
            BlockPos pos = mutable.immutable(); level.getChunkAt(pos); previous.putIfAbsent(pos, level.getBlockState(pos));
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) entities.putIfAbsent(pos, entity.saveWithFullMetadata());
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
