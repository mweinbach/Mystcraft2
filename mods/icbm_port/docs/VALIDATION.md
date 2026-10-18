# Validation — 0.1.0-alpha.2

Verified October 18, 2026 with Java 17.0.20.1, Gradle 8.8, Fabric Loom 1.6.12, Minecraft 1.19.2 and Fabric Loader 0.16.14.

| Check | Result |
| --- | --- |
| `./scripts/gradle.sh :icbm_port:build :icbm_port:runGametest` | Passed; remapped production and source JARs generated |
| JUnit | 16 passed: direct/arc flight, catalog contracts, cluster manifests, configuration validation |
| Dedicated-server GameTests | 53 passed: registration/recipes/loot, priming, launch consumption, targets/tiers/networks, payload effects and queue budgets/fairness/lifetime/reload, delivery/crafting/recovery, EMP/SAM/radar, defensive variants |
| `python3 scripts/ports.py check` | Both project ports validated |
| `python3 -m unittest discover -s scripts/tests -v` | Seven project-tooling tests passed |
| `tools/validate_resources.py <minecraft-1.19.2-client.jar>` | 553 JSON files, 856 references; 137 registered item models/names/recipe paths, 44 blockstates, eight entity names and three activating special recipes |
| Production JAR audit | Upstream MIT notice included; GameTest classes excluded |
| Whitespace check | `git diff --check` passed |

The GameTest task recreates only its disposable `build/gametest/world` before each run, preventing saved effects from contaminating later tests. Tests explicitly serialize and restore state where persistence is under test. They do not accept the Minecraft EULA or start a regular user server.

The integration work corrected armed-cart duplication, lost cluster manifests, tier-range validation, spectator defusing, malformed saved-state handling and queue starvation. Timed queued effects now retain elapsed server ticks through saves, independently of their work-budget service interval. A deterministic ballistic fixture sets the mock player's head yaw as well as body yaw.

These checks verify implemented contracts, not exact upstream feature equivalence. The static resource audit relies on this module's registration conventions; runtime recipe and loot tests provide additional coverage. Interactive client rendering, screen usability, two-client networking, full survival progression/balance and optional-mod compatibility remain unverified. A display server was unavailable in this environment, so no successful interactive client session is claimed.

See [PARITY.md](PARITY.md) for current implementation status and remaining acceptance criteria. In particular, blast geometry/propagation, radiation/toxicity, full redmatter/flying-block behavior, external energy integration, radar/EMP screens, remote chunk flight, animation/sound and extension APIs remain incomplete.
