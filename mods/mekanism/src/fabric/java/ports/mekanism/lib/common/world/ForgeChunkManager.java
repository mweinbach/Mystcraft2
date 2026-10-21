/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.world;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

/**
 * Chunks kept loaded on behalf of a block, saved with the level and offered back to the owning mod for validation
 * when the level loads. Tickets keep chunks at entity-ticking level like Forge's; random ticks in chunks without a
 * nearby player are not forced on Fabric.
 */
public final class ForgeChunkManager {

    private static final String DATA_NAME = "mekanism_port_forced_chunks";
    private static final TicketType<TicketOwner> TICKET = TicketType.create("mekanism_port:forced", Comparator.naturalOrder());
    private static final Map<String, LoadingValidationCallback> CALLBACKS = new ConcurrentHashMap<>();

    private ForgeChunkManager() {
    }

    public static void setForcedChunkLoadingCallback(String modId, LoadingValidationCallback callback) {
        CALLBACKS.put(modId, callback);
    }

    public static boolean hasForcedChunks(ServerLevel level) {
        return !data(level).mods.isEmpty();
    }

    public static boolean forceChunk(ServerLevel level, String modId, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
        ForcedChunks data = data(level);
        long chunk = ChunkPos.asLong(chunkX, chunkZ);
        Pair<LongSet, LongSet> chunks = data.mods.computeIfAbsent(modId, id -> new HashMap<>()).computeIfAbsent(owner.immutable(),
              pos -> Pair.of(new LongOpenHashSet(), new LongOpenHashSet()));
        LongSet set = ticking ? chunks.getSecond() : chunks.getFirst();
        TicketOwner ticketOwner = new TicketOwner(modId, owner.immutable(), ticking);
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (add) {
            if (!set.add(chunk)) {
                return false;
            }
            level.getChunkSource().addRegionTicket(TICKET, pos, 2, ticketOwner);
        } else {
            if (!set.remove(chunk)) {
                return false;
            }
            level.getChunkSource().removeRegionTicket(TICKET, pos, 2, ticketOwner);
            data.prune(modId, owner);
        }
        data.setDirty();
        return true;
    }

    /**
     * Lets each mod validate its saved tickets for a freshly loaded level, then loads the chunks that remain. Called by
     * the Fabric entrypoint when a server level loads.
     */
    public static void reinstatePersistentChunks(ServerLevel level) {
        ForcedChunks data = data(level);
        for (String modId : data.mods.keySet().toArray(String[]::new)) {
            LoadingValidationCallback callback = CALLBACKS.get(modId);
            if (callback == null) {
                data.mods.remove(modId);
                data.setDirty();
                continue;
            }
            callback.validateTickets(level, new TicketHelper(data, modId));
        }
        data.mods.forEach((modId, owners) -> owners.forEach((owner, chunks) -> {
            chunks.getFirst().forEach(chunk -> level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(chunk), 2, new TicketOwner(modId, owner, false)));
            chunks.getSecond().forEach(chunk -> level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(chunk), 2, new TicketOwner(modId, owner, true)));
        }));
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

        private TicketHelper(ForcedChunks data, String modId) {
            this.data = data;
            this.modId = modId;
        }

        public Map<BlockPos, Pair<LongSet, LongSet>> getBlockTickets() {
            return Collections.unmodifiableMap(data.mods.getOrDefault(modId, Collections.emptyMap()));
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
            Map<BlockPos, Pair<LongSet, LongSet>> owners = data.mods.get(modId);
            Pair<LongSet, LongSet> chunks = owners == null ? null : owners.get(owner);
            if (chunks != null && (ticking ? chunks.getSecond() : chunks.getFirst()).remove(chunk)) {
                data.prune(modId, owner);
                data.setDirty();
            }
        }

        public void removeTicket(UUID owner, long chunk, boolean ticking) {
        }
    }

    private record TicketOwner(String modId, BlockPos owner, boolean ticking) implements Comparable<TicketOwner> {

        @Override
        public int compareTo(@NotNull TicketOwner other) {
            int result = modId.compareTo(other.modId);
            if (result == 0) {
                result = owner.compareTo(other.owner);
            }
            return result == 0 ? Boolean.compare(ticking, other.ticking) : result;
        }
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
                data.mods.put(modId, owners);
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
