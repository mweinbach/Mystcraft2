**Mystcraft 2** (referred to as "Mystcraft" here onward) is a remake of the original Mystcraft mod by XCompWiz.

Mystcraft is a mod for Minecraft which allows players to create and travel to custom dimensions (referred to as "Ages" in the lore).
It is based on the popular Myst series of computer games.

# Community
Mystcraft Discord: https://discord.gg/pRGH45W

# Repository Structure
Because Minecraft is an ever shifting beast, and making a mod for it means matching versions, this repo prefixes branches and tags with the version of Minecraft the mod is targeting (ex. `mc-1.20.2/* `). This means it is feasible to make fixes to versions of Mystcraft for older versions of Minecraft, including backporting features.
- The current development branch for a version is `<mc-version>/dev`. (ex. `mc-1.20.2/dev`).
- Release tags follow the same convention, with the mod version after the slash (ex. `release/mc-1.20.2/1.0.2`)
- Mod versions across Minecraft versions may not have exact feature parity (`mc-1.20.2/1.0.2` and `mc-1.21.1/1.0.2` are not guaranteed to behave the same way).

The default branch of the repository is the current development branch of the current target version of Minecraft.
This makes it easy to see the "most current" version of the mod.

The repository has a separate tree inside it with a branch named `cla`. This is to manage the [Contributor License Agreement](https://github.com/Mystcraft/Mystcraft2/blob/cla/CLA.md).

# Contributing

1. Make a fork of the dev version of the Minecraft version you are targeting
1. Clone using git. If you are new to git, I recommend Sourcetree.
1. Make your changes
1. Push the changes back to your repo (I'd recommend using a branch)
1. Make a PR. This will initiate the automated and manual review processes.
  - Review may take a few iterations. This is normal and good! Refining changes based on other lenses makes them more robust.
    - There may even be additional design changes to features at this stage. Like all game development, the key to good design and mechanics is iteration.
1. Once a feature is approved, the branch is squashed and rebased, then merged.

## Consent
All contributors to Mystcraft must agree to the [Contributor License Agreement](https://github.com/Mystcraft/Mystcraft2/blob/cla/CLA.md).

You will be automatically asked to do so when you open a PR, and can accept the agreement by responding to the CLA assistant bot.

## Commit Messages
Mystcraft uses [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/), as that simplifies the production of change logs.

## Architecture
The mod is broken up into the following high level packages (under `net.mystcraft`):
- **core** - pure, non-Minecraft integrated or interacting code. Should not care about Minecraft versions. Preferred working environment.
- **integration** - This is the communication layer between core and Minecraft. Susceptible to Minecraft code changes, but ideally easy enough to maintain. It may reference `core` and `minecraft` freely, but should not extend any Minecraft classes. Code which loads the mod exists here.
- **minecraft** - Classes extending Minecraft classes, or directly parsing / matching Minecraft data formats. If the Minecraft version changes, things in here are almost likely to break. Minimize. Things in this package may reference `integration` or `core` directly.

Each of these high level packages is then broken up into:
- **features** (ex. "banners") - Specific mechanics and features. Features should not reference other `feature` packages, but may reference `systems`.
- **systems** - Higher level systems. Packages under `systems` may reference other systems, but not `features`. Ideally mostly glue and stitching and pathways, but some very critical mechanics may exist as systems.

**This separation makes it easier, when updating to newer versions of Minecraft, to drop any features which are difficult to update while moving forward with critical features first.**

### Features vs Systems
Prefer working in `features` over `systems`. If features should interact, the best way to handle that is a system which handles the interaction, but does not depend on the features themselves. Ideally, any such systems would be able to live in **core**.

Features should be implemented in a modular way, so that non-critical features are easy to disable (generally, by not loading them).

An example of a larger system is `linking` (dimensional teleport). However, `portals` would be a feature that uses the `linking` system.

#### Systems consuming features
For example, the linking system "consumes" the `linking` feature. This means that the system provides interface and handling for other features to use, but the logic of actually linking things exists in the feature. Systems consuming features are provided the feature during initialization. If the feature is missing or unloaded, the system should handle this gracefully.
This pattern allows most systems to exist in the **core** or **integration** layers.

## Feature Creation
Features are (ideally) designed in documentation first.

The primary way to do this is to start a Discussion regarding the feature. Get feedback and refine the concept.
Once there is a general concensus to move forward, make a branch and begin implementation.

During implementation, the feature should get a Markdown document in docs/features which explains the gameplay and implementation designs.

Note that design is not a once and done process; you will likely encounter unexpected cases and issues.
The feature design is finalized, in as much as design is ever final in a living project, only when the feature PR is approved.
See [Contributing](#Contributing) below.
