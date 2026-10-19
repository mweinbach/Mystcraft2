# Celestial implementation plan (audit only)

This document describes the next slice; it does not mark celestial parameter symbols implemented. The current color/lighting slice must be verified first. Reference: official classic commit `9bc8ddc061845df0cd5ea47f4fdef773cff786ae`.

## Observable source behavior

`AgeController.reconstruct` seeds one Java `Random` with the Age seed; `addSymbol` consumes `nextLong()` for every registered symbol, not only celestial symbols. Each celestial initializes its own `Random` from that per-symbol seed. Therefore a seed scheme based only on celestial ordinal would not reproduce the source. Until the full grammar's completed symbol stream is available, a deterministic explicit-page stream is a documented bounded approximation and must be frozen in saved data.

`SymbolSunNormal` and `SymbolMoonNormal` consume preceding factor, angle, phase, and sunset-gradient modifiers. Their periods are factor times 24000 ticks. Missing sun factors use `0.8 + 0.4 * nextDouble()`; missing moon factors use `0.2 + 1.8 * nextDouble()`. Missing angle uses `nextDouble() * 360` and rendering negates it. Explicit phase is divided by 360, then offset by -0.5. Missing phase uses a random float; when period is zero it is restricted to a half-cycle before the -0.5 offset. A zero period is a stationary body and must not divide by zero.

Orbit phase is the wrapped `(time % period + partial) / period + offset`, with the classic one-third cosine easing. Moon phase is `(time / period) % 8`, or zero for a stationary moon. Sun and moon quads use the original vanilla textures, sizes 30 and 20 at distance 100, and fade by `1-rainStrength`.

`SymbolStarsNormal` and `SymbolStarsTwinkle` consume factor, angle, and a color gradient. Their factor multiplies **240000**, not 24000. Normal stars use Java Random seed 10842 for 1500 sphere samples. Twinkle stars use ten separately generated layers of 100 sphere samples, seeded offsets, a 100-tick pulse, and the original clamped cosine brightness. Star brightness is the world's night brightness multiplied by rain visibility and gradient color.

`SunDark`, `MoonDark`, and `StarsDark` are `SymbolDummy` terminals. They satisfy grammar selections without registering a body; a dark terminal is not an instruction to erase other explicitly registered bodies. Repeated normal-body symbols register repeated bodies. `AgeController.renderCelestials` draws the registered list in order.

`StarsEndSky` is a separate six-sided tiled vanilla End-sky background tinted by a gradient (default .156 per channel). It also sets horizon zero and disables horizon/void drawing. It is not a normal star layer.

`SunsetRenderer` draws a 16-segment horizon fan for each applicable body. Its cosine-window alpha is squared, and its color uses the body gradient, then a global sunset gradient, then the source's orange fallback. Moon horizon alpha is reduced to .3. A global horizon color modifier may remain for later bodies; blindly consuming every pending gradient for an unrelated color channel is incorrect.

## Shared gameplay and rendering contract

The frozen plan needs ordered immutable `Body` and `Background` records: kind, per-symbol seed, integer period, signed yaw angle, offset, gradient, optional sunset gradient, and rendering flags. Store an explicit version and the realized defaults in the canonical saved Age entry. Reopening, joining, and restarting must never reroll them. Existing saved Ages retain their prior plan; absence of the new plan requires an explicit migration policy instead of silently changing their sky.

The compiler must share the modifier state machine with color/gradient consumers. Angle and phase average along the source's shortest-arc rule, factors average arithmetically, and each body's consumed channels are cleared while unrelated channels survive. Unsupported/dangling modifiers remain diagnostics. A completely separate pass that consumes all `modfull` pages as gradient intervals would steal a future celestial parameter; ownership of each parameter must be resolved in original page order.

The server needs the same `CelestialMath.daylightAngle` used by the client. Classic `AgeController.calculateCelestialAngle` picks the light-providing body's phase nearest day from the lower/upper extrema around .5, returning .5 when there are no suns. It does not average body angles. Only suns provide this light; moons and stars do not. The next implementation must override Age time-of-day evaluation for sky darkening and day/night gameplay and route bed sunrise advancement to the earliest moving sun dawn. Classic's no-moving-sun fallback is the next 24000 boundary. Rendering multiple suns while gameplay still follows vanilla's single sun is incomplete.

Reuse the static `mystcraft:age` dimension type. Extend the existing server-authoritative visual synchronization with bounded ordered body records; limit body count and gradient totals by the source page bound, validate finite numeric fields, and send realized seeds/settings before dimension entry. Cache by destination key, clear on disconnect, and invalidate graphics buffers on resource reload. Do not let a client submit a celestial plan.

## Loader hooks and file ownership proposal

- Shared core: `AgeCelestialSettings`, `AgeCelestialCompiler`, and `CelestialMath`; integrate the ordered modifier machine with `AgeVisualCompiler`. Add canonical frozen state through the Age manager owner. No client references in these classes.
- Shared gameplay: the Age server-level owner wires time-of-day, sky darkness, sleep sunrise, and restart behavior. It must preserve independent Age weather/time and avoid changing ordinary dimensions.
- Shared client: `AgeSkyRenderer` draws the sky dome, body quads, star buffers, horizon fans, and below-horizon/void behavior with modern vertex buffers and vanilla textures. It restores render state and closes GPU buffers on reload/disconnect. The native vanilla renderer must not also draw its default sun/moon/stars.
- Forge adapter: `DimensionSpecialEffects.renderSky` calls the shared renderer only for this effects ID.
- Fabric adapter: register `DimensionRenderingRegistry.registerSkyRenderer` for each synchronized Age key, calling the same shared renderer. Dynamic keys cannot be registered only from the initial fixed vanilla dimension list.
- Network owner: extend the visual packet schema and both login/respawn/entry/reload hooks together. Keep old frozen state backward-readable and version the presentation payload explicitly.
- Parent owns build/refmap configuration and final packaged runtime/graphical validation; no product code in this audit.

## Required evidence before acceptance

Pure tests must compare source vectors for seeds, parameter order, zero/half/full/double periods, all cardinal angle/phase modifiers, multiple suns, no sun, negative times, moon phase boundaries, and sunrise selection. Saved-data and packet roundtrips must retain every realized body and reject oversized/malformed input.

Dedicated-server tests must prove day/night and sleep agree with the planned suns, independent Ages diverge as configured, and restart preserves the plan. Graphical tests on both loaders must show two independently moving suns, multiple moons/phases, a stationary body, star rotation/twinkle, dark-body omission, End sky, and per-body sunsets. Verify no doubled vanilla bodies, no render-state leak after dimension exit, and no change in an ordinary world. Tekxit without shaders and with its optional shader pipeline need separate results; bypassed hooks are a compatibility gap, not a reason to mark effects implemented.
