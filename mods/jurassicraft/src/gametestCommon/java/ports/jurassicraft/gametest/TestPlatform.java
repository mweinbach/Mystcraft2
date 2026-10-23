package ports.jurassicraft.gametest;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import ports.jurassicraft.JurassicEvents;

/**
 * The few loader APIs the shared GameTests observe. Each loader's GameTest generator installs
 * its implementation before any shared test is created, so a shared body asserts gameplay once
 * and reaches Forge capabilities/events or Fabric's Transfer API and callbacks only through here.
 */
public interface TestPlatform {
    static TestPlatform get() { return Installed.get(); }

    /**
     * {@code blockEntity}'s inventory as hoppers and pipes see it through this loader's item API
     * (Forge's {@code ITEM_HANDLER} capability, Fabric's {@code ItemStorage.SIDED}) from {@code side},
     * or the unsided view for {@code null}; {@code null} if the loader exposes none.
     */
    @Nullable ItemAccess items(BlockEntity blockEntity, @Nullable Direction side);

    /** {@link #items}, failing if this loader exposes no item API for that face. */
    static ItemAccess automation(BlockEntity blockEntity, @Nullable Direction side) {
        ItemAccess access = get().items(blockEntity, side);
        if (access == null) throw new IllegalStateException(blockEntity + " exposes no item access from " + side);
        return access;
    }

    /**
     * Observes this loader's cancellable death hook for {@code entity} (Forge's
     * {@code LivingDeathEvent}, Fabric's {@code ServerLivingEntityEvents.ALLOW_DEATH}) until closed.
     */
    DeathWatch watchDeaths(LivingEntity entity);

    /** Runs {@code action} while this loader's mob-griefing check denies {@code entity}. */
    void denyMobGriefing(LivingEntity entity, Runnable action);

    /**
     * {@code biome}'s {@code category} spawners once this loader's natural-spawn adapter has run with
     * the current config: Forge's {@code JurassicBiomeModifier} over the original biome info, Fabric's
     * {@code BiomeModifications} selector over the loaded biome.
     */
    List<MobSpawnSettings.SpawnerData> naturalSpawns(Holder<Biome> biome, MobCategory category);

    /**
     * {@code block}'s fire odds as this loader's fire spread reads them (Forge's vanilla fire table
     * through {@code IForgeBlock}, Fabric's {@code FlammableBlockRegistry}), as
     * {@code {burnChance, spreadChance}} in {@code Platform.flammable}'s order, or {@code null} if the
     * block is not flammable.
     */
    @Nullable int[] fireOdds(ServerLevel level, BlockPos pos, Block block);

    /**
     * An empty chest loot table after this loader's loot-load adapter ({@code ForgeMod.lootSink} on
     * Forge's {@code LootTableLoadEvent} table, {@code FabricMod.lootSink} on Fabric's
     * {@code LootTableEvents.MODIFY} builder) received {@code additions}, or {@code null} if they
     * added no pool.
     */
    @Nullable LootTable loot(Consumer<JurassicEvents.LootSink> additions);

    /**
     * {@code profession}'s trades by level as this loader's trade adapter delivers JurassiCraft's
     * {@code VillageTrades} (Forge's {@code VillagerTradesEvent} lists, Fabric's
     * {@code TradeOfferHelper} registrations in {@code VillagerTrades.TRADES}).
     */
    Int2ObjectMap<List<VillagerTrades.ItemListing>> trades(VillagerProfession profession);

    /**
     * Data folders only this loader reads, mapped to the registry their entries load into (Forge's
     * {@code forge/biome_modifier}; none on Fabric, which adds biome features in code).
     */
    Map<String, Registry<?>> loaderDataRegistries(RegistryAccess access);

    /**
     * Whether this loader's record items are in vanilla's {@code RecordItem.getBySound} index. Fabric
     * builds records with vanilla's eager constructor, which indexes them; Forge's supplier-based
     * constructor deliberately skips the index (its patched jukebox renderer passes the item instead).
     */
    boolean recordsIndexedBySound();

    interface DeathWatch extends AutoCloseable {
        /** How many times the hook asked whether the entity may die. */
        int calls();
        /** Whether later deaths are cancelled. */
        void cancel(boolean cancel);
        @Override void close();
    }

    /** A face of a container as automation sees it; slots are numbered in that face's order. */
    interface ItemAccess {
        int slots();
        ItemStack stack(int slot);
        /** Inserts into one slot of the face; returns what did not fit. */
        ItemStack insert(int slot, ItemStack stack, boolean simulate);
        /** Extracts up to {@code amount} from one slot of the face. */
        ItemStack extract(int slot, int amount, boolean simulate);

        /** Inserts into whichever slots of the face accept it, as a hopper does; returns what did not fit. */
        default ItemStack insert(ItemStack stack, boolean simulate) {
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < slots() && !remaining.isEmpty(); slot++) remaining = insert(slot, remaining, simulate);
            return remaining;
        }

        /** Extracts up to {@code amount} items matching {@code match}'s item and tag from any slot of the face. */
        default ItemStack extract(ItemStack match, int amount, boolean simulate) {
            ItemStack taken = ItemStack.EMPTY;
            for (int slot = 0; slot < slots() && taken.getCount() < amount; slot++) {
                if (!ItemStack.isSameItemSameTags(stack(slot), match)) continue;
                ItemStack part = extract(slot, amount - taken.getCount(), simulate);
                if (part.isEmpty()) continue;
                if (taken.isEmpty()) taken = part;
                else taken.grow(part.getCount());
            }
            return taken;
        }
    }

    final class Installed {
        private static TestPlatform platform;

        private Installed() {}

        public static synchronized void install(TestPlatform implementation) {
            if (platform == null) platform = implementation;
        }

        static synchronized TestPlatform get() {
            if (platform == null) throw new IllegalStateException("No GameTest platform installed");
            return platform;
        }
    }
}
