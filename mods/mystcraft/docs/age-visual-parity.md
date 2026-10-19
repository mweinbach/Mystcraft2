# Age visuals: implementation and classic reference

This document describes current source behavior, not a new runtime pass. Exact
artifact hashes, executed scenario counts and graphical captures belong in
[validation.md](validation.md). The reference is official classic Mystcraft1.12,
commit `9bc8ddc061845df0cd5ea47f4fdef773cff786ae`; this is not a Mystcraft2 design.

## Current compilation and frozen presentation

New Age allocation uses the completed classic grammar execution, including its
independent per-terminal seed stream. The ordered visual compiler consumes color,
gradient, factor, angle and phase queues. Repeated lighting controllers use source
last-controller precedence; extra-controller and dangling/clear costs belong to
the classic score pipeline, rather than arbitrary unsupported-page penalties.
Unknown handlers and invalid payloads still reject before canonical allocation.
The older bounded explicit-page parser remains a compatibility API; it is not the
production completion algorithm for new Ages.

`AgeVisualSettings` freezes presentation independently into `MystcraftVisual` in
the canonical Age entry. Version3 adds environment fields to the earlier color
and celestial settings. Saved visual versions1/2 remain readable and retain their
earlier native cloud-height/environment defaults; existing profiles are not
silently recompiled when symbols or random completion change.

A server-authoritative wire-v3 channel sends bounded settings on login, respawn,
dimension entry and reload. Destination settings can be sent before travel and
cached before the client level exists. The dimension-keyed client cache is capped
at4096 entries and cleared on disconnect. Updating current settings invalidates
tint caches and terrain rendering. Forge requires presentation protocol3; Fabric
uses `mystcraft:age_visuals_v3`, so stale clients are rejected explicitly.

Ages use the dedicated `mystcraft:age` dimension effects identifier. Shared visual
math/rendering has thin loader adapters and client-only hooks. No shared biome
instance or Overworld effects registration is mutated. The ordinary-world path
returns native colors/settings when no Mystcraft Age profile applies.

## Implemented controls

- Sixteen color modifiers, ordered averaging, gradient intervals and their
  consumers: day/night sky, fog, water, clouds, grass and foliage, including
  natural variants. Gradients sample day time in12000-tick units. Dynamic
  providers average in registration order. Source static-color combination
  discards the return value of `Color.average`, so the first water/grass/foliage
  provider wins; integer conversion truncates channels as classic did.
- Normal/dark/bright light-table math runs before native gamma/darkness processing.
  The classic integer `scaleLighting` helper has no audited main-source callers;
  no invented server light effect is derived from that unused API.
- Multiple suns/moons/stars with independent seeded periods, angles and phases,
  stationary bodies, dark terminals, twinkle stars, End sky and individual/global
  horizon gradients. Server daylight and sleep scheduling use the frozen suns.
- Source Rainbow consumes only angle, uses seeded yaw when absent, and remains
  stationary and visible independently of weather/time. Its two semicircles use
  the source HSV bands, geometry and alpha. The invalid first source square-root
  patch is clipped instead of uploading NaN vertices.
- Classic cloud-height/horizon contributions and their ordered averaging, including
  Skylands42.5 and Nether200 cloud heights. NoHorizon suppresses both lower sky
  surfaces in completed custom-sky profiles. Native cloud geometry and video
  settings remain in charge.
- Rain/Snow/Storm/Cloudy distinguish weather strength, precipitation type and
  temperature. Rain/Storm force liquid rain and clamp temperature up to0.20;
  Snow forces snowfall and clamps down to0.10; Cloudy darkens without precipitation.
  The four forced controllers retain the source persistent12000-tick disable
  cooldown after a precipitation reset. Server snow/ice checks use native
  placement rules with scoped temperature overrides.

The source sky math applies rain/thunder/lightning blending after sky providers.
Clouds retain their distinct0.95 weather-blend factors and daylight multipliers.
Fog keeps the source nonzero replacement for pure black and natural blue baseline.
The upper disk now uses radial fog-color interpolation because the modern
position/color shader lacks the source fixed-function GL_FOG behavior.

## Explicit adaptation and verification limits

Natural grass/foliage sampling and modern neighborhood tint blending are adapters,
not a claim of pixel-identical1.12 biome wrapping. Server temperature overrides
cover weather/rain/snow/ice paths; unrelated entity/mod temperature reads outside
those scopes can still see registered biome temperature. Weather-strength
interpolation, lightning positioning and clouds use modern native behavior.
Existing legacy/native-sky saved profiles keep their earlier behavior.

The radial sky/fog blend substitutes for fixed-function fog and needs graphical
comparison. Shaders and third-party render pipelines can bypass native hooks;
Tekxit/Iris compatibility is not established by builds or dedicated-server smoke.
Packet timing, disconnect/rejoin behavior, ordinary-world isolation and real
client rendering must be reported against actual packaged artifacts. This source
overview makes no new test or graphical-pass claim.

## Catalog and detailed evidence

Both catalog copies are byte-identical and mark145/145 fixed definitions as
`modern_implemented`: every fixed symbol has a traceable executing modern path.
This is an implementation flag, not a full-classic parity or145-symbol runtime
coverage claim. Original full-classic reference `implemented` fields remain
separate. Dynamic biome/fluid families are outside that fixed count. Acquisition
ranks and operator exclusions remain independent of this flag.

See [static symbol audit](static-symbol-implementation-audit.md),
[celestial implementation](celestial-implementation.md),
[environment implementation](environment-implementation.md),
[operator tuning](symbol-tuning.md) and
[graphical fixture commands](environment-test-fixtures.md) for exact scope.
