# Classic linking permissions, identity and home rules

The pinned `LinkListenerPermissions` rules apply to players using books, stands, physical books, portals and natural fissures. Departure and entry are independent. Restrictions win; a present permit set is a whitelist (including an empty set that permits nothing); absent rules allow travel. A server operator using a book does not automatically bypass these rules. Only explicit operator travel/home recovery uses the source `TPCOMMAND` bypass. Loader travel cancellation and destination safety still apply.

Permissions use exact GameProfile usernames in overworld SavedData `mystcraft-linking-permissions`. This follows the source username model while avoiding mutable decorated display names. The old source's `PermitData` versus `PermitDepart` load typo and destructive list-reader loop are not reproduced; both directions survive save/load without changing input NBT.

`/myst-permissions <player> permit|restrict entry|depart <dimension|Age number|all>` requires permission level4, matching inherited source command defaults. `restrict ... all` clears restrictions and creates an empty permit whitelist. `permit ... all` resets that direction to unrestricted. Permitting one destination adds it to an existing whitelist and removes its restriction; it does not create a whitelist when none exists. Future dimension keys and canonical Age numbers may be configured before the world is loaded.

`config/mystcraft-linking.json` is created with source defaults:

```json
{
  "respawnInAges": true,
  "requireUUID": false,
  "homeDimension": "minecraft:overworld"
}
```

Boolean types, the home registry key and unknown settings are validated before login; invalid configuration rejects startup instead of selecting a guessed fallback world. Restart after editing. The home world must exist, load successfully, not be marked dead, and have a bounded safe spawn. A failed recovery never consumes inventory or silently creates a dimension; the player receives a clear disconnect message so the operator can repair the home configuration.

Player identity is persisted on `ServerPlayer`, copied through native `restoreFrom`, and updated after real dimension changes. On login, missing identity triggers home recovery only when `requireUUID` is true; a mismatched identity or dead world triggers recovery regardless. Age identities are canonical random UUIDs in AgeSavedData. Source non-Mystcraft identities were `new UUID(numericDimensionId,0)`, stable across independent worlds; modern native/mod dimensions use a deterministic UUID of their registry key with that same cross-save meaning. This is not a world-isolation security boundary. Ordinary target books stamp `TargetUUID` when bound, descriptive books stamp canonical Age UUIDs, and mismatched/malformed values reject. Missing target UUID remains accepted as in source; `requireUUID` concerns player login, not old book format.

`/tpx [player] <dimension|Age number|online target player> [x y z]` is operator-only explicit permission-bypass travel. Coordinates accept finite absolute numbers or `~` offsets. Registry keys replace legacy numeric vanilla dimensions; positive numbers identify canonical Ages. Native travel vetoes, world borders, height, liquids and collision still reject unsafe destinations. No book is consumed. Online player names are supported; arbitrary entity selectors are not claimed.

`/myst-create [title]` requires permission level4 and compiles the source-style panel-only/default grammar, reserves a new canonical Age identity and loads its actual runtime world. Unlike the old integer-ID command, it does not overwrite an arbitrary numeric dimension slot. The server chooses the next canonical Age number. `/myst-agebook [Age number]` and `/mystcraft book [Age number]` recover descriptive books for existing identities without allocating another Age. Existing vanilla commands remain registered.

The modern respawn implementation uses loader events and safe transfer rather than rewriting persistent bed data. Exact no-bed/current-Age behavior is validated separately from identity-copy and post-respawn home-policy tests; those tests must not be mistaken for a graphical death/respawn session.
