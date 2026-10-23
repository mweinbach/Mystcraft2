package ports.jurassicraft.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.loot.v2.LootTableSource;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import ports.jurassicraft.JurassicEvents;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.platform.Platform;
import ports.jurassicraft.worldgen.NaturalSpawns;
import ports.jurassicraft.worldgen.WorldgenPlacements;

/** Fabric entrypoint: installs the platform and forwards Fabric events to the shared handlers. */
public final class FabricMod implements ModInitializer {
    @Override public void onInitialize() {
        FabricPlatform platform = new FabricPlatform();
        Platform.install(platform);
        FabricConfigBackend.loadCommon();
        PortMod.init();
        platform.flush();
        PortMod.setup();

        JurassicEvents.attributes(FabricDefaultAttributeRegistry::register);
        JurassicEvents.spawnPlacements(new JurassicEvents.SpawnPlacementSink() {
            @Override public <T extends Mob> void register(EntityType<T> type, SpawnPlacements.Type placement, Heightmap.Types heightmap,
                    SpawnPlacements.SpawnPredicate<T> predicate) {
                SpawnPlacements.register(type, placement, heightmap, predicate);
            }
        });
        biomes();

        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> JurassicEvents.commands(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(JurassicEvents::serverAboutToStart);
        // Forge re-reads edited config files through ModConfigEvent.Reloading; Fabric re-reads them on /reload.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> FabricConfigBackend.reload());
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> JurassicEvents.chunkLoaded(level, chunk));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> JurassicEvents.entityJoined(entity));
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> JurassicEvents.blockBroken(level, player, pos, state));
        LootTableEvents.MODIFY.register((resources, loot, id, table, source) -> modifyLootTable(id, table, source));
        JurassicEvents.trades((profession, level, listing) -> TradeOfferHelper.registerVillagerOffers(profession, level, trades -> trades.add(listing)));

        FabricPlatform.registerServerReceivers();
    }

    /**
     * Like Forge's LootTableLoadEvent, which never fires for tables a datapack replaces, only tables
     * that still come from the game or a mod receive the bonus loot. Public so in-world tests use it.
     */
    public static void modifyLootTable(ResourceLocation id, LootTable.Builder table, LootTableSource source) {
        if (source.isBuiltin()) JurassicEvents.lootTable(id, lootSink(table));
    }

    /** Public so in-world tests exercise the same adapter. */
    public static JurassicEvents.LootSink lootSink(LootTable.Builder table) { return (name, pool) -> table.withPool(pool); }

    private static void biomes() {
        for (WorldgenPlacements.Placement placement : WorldgenPlacements.all()) {
            if (placement.afterOtherFeatures()) {
                // After ordinary additions, so coal placed in later decoration steps is already there.
                BiomeModifications.create(placement.feature().location()).add(ModificationPhase.POST_PROCESSING,
                        context -> placement.enabled() && context.hasTag(placement.biomes()),
                        context -> context.getGenerationSettings().addFeature(placement.step(), placement.feature()));
            } else {
                BiomeModifications.addFeature(context -> placement.enabled() && context.hasTag(placement.biomes()), placement.step(), placement.feature());
            }
        }
        for (NaturalSpawns.Entry entry : NaturalSpawns.entries()) {
            BiomeModifications.addSpawn(context -> accepts(entry, context), entry.category(), entry.type(),
                    entry.weight(), entry.minCount(), entry.maxCount());
        }
    }

    private static boolean accepts(NaturalSpawns.Entry entry, BiomeSelectionContext context) {
        NaturalSpawns.BiomeView view = new NaturalSpawns.BiomeView() {
            @Override public boolean is(TagKey<Biome> tag) { return context.hasTag(tag); }
            @Override public boolean spawns(MobCategory category, EntityType<?> type) {
                return context.getBiome().getMobSettings().getMobs(category).unwrap().stream().anyMatch(spawn -> spawn.type == type);
            }
        };
        return entry.rule().accepts(view) && !view.spawns(entry.category(), entry.type());
    }
}
