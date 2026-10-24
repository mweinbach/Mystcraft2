/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.world;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import ports.projecte.lib.fml.ModList;

/**
 * Block-owned chunk tickets saved with the level and offered back to the owning mod when the level loads.
 *
 * <p>Vanilla turns the distance passed to {@code addRegionTicket} into ticket level {@code 33 - distance}. Distance
 * {@link #TICKET_DISTANCE} is level 31, the same level as {@code /forceload}, so the chunk, its entities and its block
 * entities tick. Random ticks still run only near a player: vanilla tickets have no equivalent of Forge's
 * {@code forceTicks} flag. {@code ticking} is stored so a loader such as the dimensional stabilizer can be told apart
 * from an anchor upgrade when the tickets are validated, and it does not enable random ticks.
 */
public final class ForgeChunkManager {

    public static final String BLOCK_TICKET = "mekanism_port:block";
    public static final String BLOCK_TICKING_TICKET = "mekanism_port:block_ticking";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "mekanism_port_forced_chunks";
    private static final int TICKET_DISTANCE = 2;
    private static final TicketType<BlockOwner> BLOCK = TicketType.create(BLOCK_TICKET, BlockOwner::compareTo);
    private static final TicketType<BlockOwner> BLOCK_TICKING = TicketType.create(BLOCK_TICKING_TICKET, BlockOwner::compareTo);
    private static final Map<String, LoadingValidationCallback> CALLBACKS = new ConcurrentHashMap<>();
    /**
     * While a level is reapplying saved tickets, {@link #forceChunk} only edits the save. The runtime tickets are added
     * once, after validation, so a callback can add and remove saved tickets without orphaning a vanilla ticket.
     */
    private static int reinstateDepth;

    private ForgeChunkManager() {
    }

    public static void setForcedChunkLoadingCallback(String modId, LoadingValidationCallback callback) {
        if (!ModList.get().isLoaded(modId)) {
            LOGGER.warn("A mod attempted to set the forced chunk validation callback for an unloaded mod of id: {}", modId);
            return;
        }
        CALLBACKS.put(modId, callback);
    }

    public static boolean hasForcedChunks(ServerLevel level) {
        if (!level.getForcedChunks().isEmpty()) {
            return true;
        }
        ForcedChunks data = saved(level);
        return data != null && !data.mods.isEmpty();
    }

    public static boolean hasBlockTicket(ServerLevel level, String modId, BlockPos owner, int chunkX, int chunkZ, boolean ticking) {
        ForcedChunks data = saved(level);
        if (data == null) {
            return false;
        }
        Pair<LongSet, LongSet> chunks = chunks(data, modId, owner);
        return chunks != null && set(chunks, ticking).contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public static boolean forceChunk(ServerLevel level, String modId, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        if (!ModList.get().isLoaded(modId)) {
            LOGGER.warn("A mod attempted to force a chunk for an unloaded mod of id: {}", modId);
            return false;
        }
        ForcedChunks data = data(level);
        BlockPos key = owner.immutable();
        long chunk = ChunkPos.asLong(chunkX, chunkZ);
        Map<BlockPos, Pair<LongSet, LongSet>> owners = data.mods.get(modId);
        Pair<LongSet, LongSet> chunks = owners == null ? null : owners.get(key);
        if (add) {
            if (owners == null) {
                owners = new HashMap<>();
                data.mods.put(modId, owners);
            }
            if (chunks == null) {
                chunks = Pair.of(new LongOpenHashSet(), new LongOpenHashSet());
                owners.put(key, chunks);
            }
            if (!set(chunks, ticking).add(chunk)) {
                return false;
            }
        } else if (chunks == null || !set(chunks, ticking).remove(chunk)) {
            return false;
        } else {
            data.prune(modId, key);
        }
        data.setDirty();
        if (reinstateDepth == 0) {
            SavedTicket ticket = new SavedTicket(modId, key, chunk, ticking);
            if (add) {
                addRuntime(level, ticket);
                level.getChunk(chunkX, chunkZ);
            } else {
                removeRuntime(level, ticket);
            }
        }
        return true;
    }

    /**
     * Drops the vanilla tickets and keeps the saved owners. The next {@link #reinstatePersistentChunks} loads them again.
     */
    public static void suspendRuntimeTickets(ServerLevel level) {
        ForcedChunks data = saved(level);
        if (data == null) {
            return;
        }
        for (SavedTicket ticket : snapshot(data)) {
            removeRuntime(level, ticket);
        }
    }

    /**
     * Lets each mod validate its saved tickets for a freshly loaded level, then loads the chunks that remain. Called when
     * a server level loads.
     */
    public static void reinstatePersistentChunks(ServerLevel level) {
        ForcedChunks data = saved(level);
        if (data == null || data.mods.isEmpty()) {
            return;
        }
        reinstateDepth++;
        try {
            for (String modId : data.mods.keySet().toArray(String[]::new)) {
                LoadingValidationCallback callback = CALLBACKS.get(modId);
                if (callback == null) {
                    data.mods.remove(modId);
                    data.setDirty();
                    LOGGER.warn("Removing saved chunk tickets for unavailable mod {}", modId);
                    continue;
                }
                callback.validateTickets(level, new TicketHelper(data, modId));
            }
            for (SavedTicket ticket : snapshot(data)) {
                addRuntime(level, ticket);
                ChunkPos pos = new ChunkPos(ticket.chunk);
                level.getChunk(pos.x, pos.z);
            }
        } finally {
            reinstateDepth--;
        }
    }

    private static void addRuntime(ServerLevel level, SavedTicket ticket) {
        level.getChunkSource().addRegionTicket(type(ticket.ticking), new ChunkPos(ticket.chunk), TICKET_DISTANCE, new BlockOwner(ticket.modId, ticket.owner));
    }

    private static void removeRuntime(ServerLevel level, SavedTicket ticket) {
        level.getChunkSource().removeRegionTicket(type(ticket.ticking), new ChunkPos(ticket.chunk), TICKET_DISTANCE, new BlockOwner(ticket.modId, ticket.owner));
    }

    private static TicketType<BlockOwner> type(boolean ticking) {
        return ticking ? BLOCK_TICKING : BLOCK;
    }

    private static LongSet set(Pair<LongSet, LongSet> chunks, boolean ticking) {
        return ticking ? chunks.getSecond() : chunks.getFirst();
    }

    @Nullable
    private static Pair<LongSet, LongSet> chunks(ForcedChunks data, String modId, BlockPos owner) {
        Map<BlockPos, Pair<LongSet, LongSet>> owners = data.mods.get(modId);
        return owners == null ? null : owners.get(owner);
    }

    private static List<SavedTicket> snapshot(ForcedChunks data) {
        List<SavedTicket> tickets = new ArrayList<>();
        data.mods.forEach((modId, owners) -> owners.forEach((owner, chunks) -> {
            chunks.getFirst().forEach((long chunk) -> tickets.add(new SavedTicket(modId, owner, chunk, false)));
            chunks.getSecond().forEach((long chunk) -> tickets.add(new SavedTicket(modId, owner, chunk, true)));
        }));
        return tickets;
    }

    @Nullable
    private static ForcedChunks saved(ServerLevel level) {
        return level.getDataStorage().get(ForcedChunks::load, DATA_NAME);
    }

    private static ForcedChunks data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ForcedChunks::load, ForcedChunks::new, DATA_NAME);
    }

    public interface LoadingValidationCallback {

        void validateTickets(ServerLevel level, TicketHelper ticketHelper);
    }

    public static class TicketHelper {

        private final ForcedChunks data;
        private final String modId;
        private final Map<BlockPos, Pair<LongSet, LongSet>> blockTickets;

        private TicketHelper(ForcedChunks data, String modId) {
            this.data = data;
            this.modId = modId;
            Map<BlockPos, Pair<LongSet, LongSet>> copy = new HashMap<>();
            Map<BlockPos, Pair<LongSet, LongSet>> owners = data.mods.get(modId);
            if (owners != null) {
                for (Map.Entry<BlockPos, Pair<LongSet, LongSet>> entry : owners.entrySet()) {
                    copy.put(entry.getKey(), Pair.of(new LongOpenHashSet(entry.getValue().getFirst()), new LongOpenHashSet(entry.getValue().getSecond())));
                }
            }
            this.blockTickets = Collections.unmodifiableMap(copy);
        }

        /**
         * Tickets this mod had saved. The first set of each pair is not fully ticking and the second set is. The map is a
         * snapshot, so removing tickets while iterating it is safe.
         */
        public Map<BlockPos, Pair<LongSet, LongSet>> getBlockTickets() {
            return blockTickets;
        }

        public Map<UUID, Pair<LongSet, LongSet>> getEntityTickets() {
            return Collections.emptyMap();
        }

        public void removeAllTickets(BlockPos owner) {
            Map<BlockPos, Pair<LongSet, LongSet>> owners = data.mods.get(modId);
            if (owners != null && owners.remove(owner) != null) {
                data.prune(modId, owner);
                data.setDirty();
            }
        }

        public void removeAllTickets(UUID owner) {
        }

        public void removeTicket(BlockPos owner, long chunk, boolean ticking) {
            Pair<LongSet, LongSet> chunks = chunks(data, modId, owner);
            if (chunks != null && set(chunks, ticking).remove(chunk)) {
                data.prune(modId, owner);
                data.setDirty();
            }
        }

        public void removeTicket(UUID owner, long chunk, boolean ticking) {
        }
    }

    private record BlockOwner(String modId, BlockPos owner) implements Comparable<BlockOwner> {

        @Override
        public int compareTo(@NotNull BlockOwner other) {
            int result = modId.compareTo(other.modId);
            return result == 0 ? owner.compareTo(other.owner) : result;
        }
    }

    private record SavedTicket(String modId, BlockPos owner, long chunk, boolean ticking) {
    }

    private static final class ForcedChunks extends SavedData {

        private final Map<String, Map<BlockPos, Pair<LongSet, LongSet>>> mods = new HashMap<>();

        private static ForcedChunks load(CompoundTag tag) {
            ForcedChunks data = new ForcedChunks();
            for (String modId : tag.getAllKeys()) {
                Map<BlockPos, Pair<LongSet, LongSet>> owners = new HashMap<>();
                for (Tag entry : tag.getList(modId, Tag.TAG_COMPOUND)) {
                    CompoundTag ownerTag = (CompoundTag) entry;
                    owners.put(NbtUtils.readBlockPos(ownerTag.getCompound("Owner")), Pair.of(new LongOpenHashSet(ownerTag.getLongArray("Chunks")),
                          new LongOpenHashSet(ownerTag.getLongArray("TickingChunks"))));
                }
                if (!owners.isEmpty()) {
                    data.mods.put(modId, owners);
                }
            }
            return data;
        }

        @NotNull
        @Override
        public CompoundTag save(@NotNull CompoundTag tag) {
            mods.forEach((modId, owners) -> {
                ListTag list = new ListTag();
                owners.forEach((owner, chunks) -> {
                    CompoundTag ownerTag = new CompoundTag();
                    ownerTag.put("Owner", NbtUtils.writeBlockPos(owner));
                    ownerTag.put("Chunks", new LongArrayTag(chunks.getFirst().toLongArray()));
                    ownerTag.put("TickingChunks", new LongArrayTag(chunks.getSecond().toLongArray()));
                    list.add(ownerTag);
                });
                tag.put(modId, list);
            });
            return tag;
        }

        private void prune(String modId, BlockPos owner) {
            Map<BlockPos, Pair<LongSet, LongSet>> owners = mods.get(modId);
            if (owners != null) {
                Pair<LongSet, LongSet> chunks = owners.get(owner);
                if (chunks != null && chunks.getFirst().isEmpty() && chunks.getSecond().isEmpty()) {
                    owners.remove(owner);
                }
                if (owners.isEmpty()) {
                    mods.remove(modId);
                }
            }
        }
    }
}
