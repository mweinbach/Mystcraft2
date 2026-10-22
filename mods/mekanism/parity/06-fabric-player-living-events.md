## Fabric: player and living events

These fire from the same vanilla methods Forge 43.5.0 patches. Listeners on the port's bus see them, so the server
applies jetpack thrust, scuba air, free-runner fall absorption, radiation, step assist and the MekaSuit's break-speed
modules. `onArmorTick` already ran; the modules that only needed a player tick now run with it.

- Player tick, start and end. The server end tick is what drives the jetpack, scuba tank, radiation and step assist.
  Step assist and the hydrostatic repulsor write Forge's step-height and swim-speed attributes. Living entities have
  those attributes, players also have reach distance, collision steps up by the step-height attribute, and swimming
  scales by swim speed. The client publishes the ascend key from `ClientTickEvent` start, which the client tick
  wiring posts, so a normal-mode jetpack follows the jump key.
- Living tick, attack, hurt, damage, fall, jump and death. A non-player posts living attack from `LivingEntity.hurt`.
  A player posts it from `Player.hurt` and skips the living-entity copy, matching Forge. Death is posted from
  `LivingEntity.die`, `Player.die` and `ServerPlayer.die`, so an uncancelled player death posts three times.
  `ServerPlayer` posts after its death game event. Living damage is posted from the health change inside
  `actuallyHurt`, and the adjusted amount is what combat tracking, food exhaustion, damage statistics and the
  following absorption update use. Cancelling the event skips that bookkeeping. The attacker's absorbed-damage
  statistic stays the absorption applied before the event. When armor and absorption already reduced the damage to
  zero, vanilla never changes health, so that zero-damage event is not posted.
- Break speed, including the position `getDestroyProgress` is measuring. Direct `getDestroySpeed` calls pass an empty
  position. A cancelled break speed returns -1, which stops the break. This is the hook the Meka-Tool, atomic
  disassembler and MekaSuit gyroscopic stabilization use.
- Item crafted, after `ItemStack.onCraftedBy` in the crafting result slot, and the crafting player is set around the
  remainder lookup. Bin insert recipes consume the rest of the grid through this event.
- Entity join, cancellable, before the entity is added. A fresh or loaded entity is posted from
  `PersistentEntitySectionManager.addEntity`, and a cancelled join is not added. A cardboard box uses it to swallow
  dropped items and experience. A player is posted once from `ServerLevel.addPlayer`, before a duplicate UUID is
  removed, with `loadedFromDisk` false. The section manager then adds that player without posting again.
- Entity leave, from Fabric's server entity unload, after the entity stops being tracked. Forge also posts it on the
  client; the client copy is unwired.
