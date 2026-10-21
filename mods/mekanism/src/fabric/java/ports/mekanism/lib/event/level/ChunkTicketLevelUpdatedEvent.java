/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event.level;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.eventbus.api.Event;

public class ChunkTicketLevelUpdatedEvent extends Event {

    private final ServerLevel level;
    private final long chunkPos;
    private final int oldTicketLevel;
    private final int newTicketLevel;
    @Nullable
    private final ChunkHolder chunkHolder;

    public ChunkTicketLevelUpdatedEvent(ServerLevel level, long chunkPos, int oldTicketLevel, int newTicketLevel, @Nullable ChunkHolder chunkHolder) {
        this.level = level;
        this.chunkPos = chunkPos;
        this.oldTicketLevel = oldTicketLevel;
        this.newTicketLevel = newTicketLevel;
        this.chunkHolder = chunkHolder;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public long getChunkPos() {
        return chunkPos;
    }

    public int getOldTicketLevel() {
        return oldTicketLevel;
    }

    public int getNewTicketLevel() {
        return newTicketLevel;
    }

    @Nullable
    public ChunkHolder getChunkHolder() {
        return chunkHolder;
    }
}
