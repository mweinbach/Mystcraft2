# Defense adaptations

The Fabric defense slice adapts upstream `ItemRadarGun`, `ItemAntidote`,
`ItemDefuser`, radar station, EMP tower and SAM concepts. It uses original Java
and vanilla item textures. It does not claim full upstream parity.

* Radar tracks both explosive and held-item missile families and scans only loaded missiles within a 128-block sphere every second.
  It emits a redstone strength capped at 15 and lists up to eight coordinates
  when used. EMP suppresses its output.
* EMP towers emit a 32-block pulse each second while powered. EMP payloads emit
  50-block pulses. Missile and launcher electronics are disabled for 200 ticks.
  Fields persist per dimension with absolute game-time expiry, bounded to 256
  fields and radius 128. Reload preserves remaining duration. Queries use loaded
  entities; neither missiles nor defenses load chunks.
* Load up to 15 firework rockets into a SAM launcher, then apply redstone. Once
  per second it targets the nearest unassigned approaching missile within 64
  blocks whose projected trajectory passes within 16 blocks of the launcher.
  Stationary missiles are also eligible. Receding and EMP-disabled missiles are
  ignored. A successful interceptor spawn consumes exactly one charge.
* SAM interceptors are real guided entities rendered as firework items. They
  predict the target's movement, steer at up to three blocks per tick, and
  neutralize the target without detonating its payload when within 1.5 blocks
  of their flight segment. Solid-block collisions destroy the interceptor.
  Target UUID, elapsed lifetime and vanilla position/velocity persist through
  save/reload. Missing/unloaded targets, unloaded terrain, world boundaries,
  EMP-disabled targets or a 100-tick lifetime end the flight without explosions.
* Radar guns trace blocks up to 200 blocks away and share launcher coordinate
  NBT; sneak-use clears the target. Antidote consumes after 32 ticks and clears
  all status effects. Defusers attack primed explosives, missiles and TNT,
  recovering ammunition and spending one of 128 durability points.
* Normal, compact and reinforced concrete retain upstream hardness 10 and
  resistance values 28, 280 and 2800, respectively. These numeric values use
  Minecraft 1.19.2 explosion mechanics; exact cross-version blast equivalence
  is unverified. Reinforced glass resistance is 60.
* Normal, poison and fire spikes permit entity overlap and deal one point of
  cactus-type damage. Poison applies 140 ticks of poison; fire ignites for seven
  seconds, matching upstream defaults. Glass buttons and plates use vanilla
  button and all-entity pressure-plate behavior.

Still missing: Forge energy storage/costs, radio networks and machine GUIs,
EMP tower multiblocks, upstream SAM configuration and missile models, ownership
or faction-based target discrimination,
hazmat equipment and radioactive material handling. Fireworks are a temporary
interceptor ammunition substitute. Redstone replaces upstream energy control.

`DefenseGameTests` covers EMP sphere bounds, radar output, powered interception
and exact ammunition consumption, interceptor persistence, receding-target
rejection, wall collision, EMP field save/reload/expiry/caps, and poison/fire
spike effects. Client rendering remains a manual check.

Held-item flight integration: radar and SAM use the same bounded loaded-flight
query for explosive missiles and held-item missiles. EMP and defusing recover
held missiles intact without running their cargo interaction. Guided interception
also safely recovers held cargo. Dedicated tests cover radar/threat/defuser family
recognition, EMP cancellation and actual guided interception of a held flight.
