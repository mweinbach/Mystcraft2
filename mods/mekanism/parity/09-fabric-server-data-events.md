## Fabric: server data events

These fire from the same vanilla methods Forge 43.5.0 patches. Listeners on the port's bus see them, so chunk retrogen,
the saved world-gen version and the recipe cache reload run on a server.

- Chunk data save, from `ChunkMap.save`, after `ChunkSerializer.write` and before that tag is written. The level is
  the chunk's world, or the chunk map's level when the chunk has none. `CommonWorldTickHandler` writes
  `mekWorldGenVersion` into that tag.
- Chunk data load, from both returns of `ChunkSerializer.read`. A full chunk is posted as the `LevelChunk` Forge posts
  before wrapping it, so the level is present. A proto chunk's level is absent, matching `getWorldForge`. An older
  `mekWorldGenVersion` is remembered, and when regeneration is enabled the chunk is queued. A later save keeps the old
  version until that regeneration has run.
- `ChunkTicketLevelUpdatedEvent`, from the end of `ChunkMap.updateChunkScheduling`, with Forge's arguments: the old
  and new level and the holder the method returns. Like Forge it is not posted from the early return taken when both
  levels are past `MAX_CHUNK_DISTANCE`, or when the level did not change. `TransmitterNetworkRegistry` uses it to take
  cables, pipes and transporters out of their networks when their chunk stops being accessible (level 34 and above)
  and to put them back when it is accessible again.
- `AddReloadListenerEvent`, from `ReloadableServerResources.loadResources`, before the reload listeners run. Listeners
  added during the event are appended after vanilla's and run in that same reload, in registration order. Mekanism
  registers at lowest priority, so its recipe-cache listener runs last: `/reload` clears the cache and sets the flag
  machines read on the next tick.
- Entity join stays cancellable. A cancelled join is not added. The player post is the one described with the player
  and living events.
