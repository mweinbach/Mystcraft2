package ports.jurassicraft;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootPool;
import ports.jurassicraft.bugs.BugHarvest;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.entity.AnimateCommand;
import ports.jurassicraft.entity.DinosaurRegistry;
import ports.jurassicraft.goat.GoatRegistry;
import ports.jurassicraft.loot.BonusLoot;
import ports.jurassicraft.maps.VisitorCentreMaps;
import ports.jurassicraft.music.MusicLoot;
import ports.jurassicraft.structures.SpawnParkCommand;
import ports.jurassicraft.structures.VisitorLocate;
import ports.jurassicraft.village.PaleontologistDigGoal;
import ports.jurassicraft.village.VillagePools;
import ports.jurassicraft.village.VillageTrades;

/**
 * Gameplay reactions to loader lifecycle and world events. Each loader adapter subscribes to its
 * own event of the same meaning and forwards here, so both loaders run identical handlers.
 */
public final class JurassicEvents {
    public interface SpawnPlacementSink {
        <T extends Mob> void register(EntityType<T> type, SpawnPlacements.Type placement, Heightmap.Types heightmap,
                SpawnPlacements.SpawnPredicate<T> predicate);
    }

    /** Adds a pool to the loot table being loaded; adapters skip names the table already has. */
    public interface LootSink {
        void addPool(String name, LootPool.Builder pool);
    }

    public interface TradeSink {
        void add(VillagerProfession profession, int level, VillagerTrades.ItemListing listing);
    }

    private JurassicEvents() {}

    public static void attributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> sink) {
        DinosaurRegistry.attributes(sink);
        GoatRegistry.attributes(sink);
    }

    public static void spawnPlacements(SpawnPlacementSink sink) {
        DinosaurRegistry.spawnPlacements(sink);
        GoatRegistry.spawnPlacements(sink);
    }

    public static void commands(CommandDispatcher<CommandSourceStack> dispatcher) {
        VisitorLocate.install(dispatcher.getRoot());
        SpawnParkCommand.register(dispatcher);
        AnimateCommand.register(dispatcher);
    }

    public static void serverAboutToStart(MinecraftServer server) {
        VillagePools.addVillageBuildings(server);
    }

    public static void entityJoined(Entity entity) {
        PaleontologistDigGoal.onJoin(entity);
    }

    public static void blockBroken(Level level, Player player, BlockPos pos, BlockState state) {
        BugHarvest.harvest(level, player, pos, state);
    }

    public static void lootTable(ResourceLocation id, LootSink sink) {
        int percentage = JurassicConfig.LOOT_SPAWNRATE.get();
        BonusLoot.addTo(id, sink, percentage);
        MusicLoot.addTo(id, sink, percentage);
    }

    public static void trades(TradeSink sink) {
        VillageTrades.addTo(sink);
        VisitorCentreMaps.addTo(sink);
    }
}
