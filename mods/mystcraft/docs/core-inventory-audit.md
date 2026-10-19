# Remaining core inventory audit

Source scope is the pinned classic 1.12 tree, including ModItems/ModBlocks,
ModRecipes and JSON recipes, ItemLinking, ItemInkVial/ModFluids,
LinkListenerPermissions, MystcraftConnectionHandler, and WorldProviderMyst.
This inventory is a source comparison, not evidence of completed runtime tests.

## Items and recipes

The survival content inventory includes pages, descriptive/normal/unlinked
books, boosters, folders, portfolios, ink vials, writing desk/extension,
binder, mixer, bookstand, lectern, crystal/receptacle/portal, decay, modifier
and star fissure. Those have corresponding modern implementations. Ink vial
water-bottle and water-bucket recipes and the custom panel-to-unlinked-book
recipe require loaded RecipeManager verification, not just JSON name matching.
Following books must have the original glint; the shared item now implements
that rendering predicate.

Classic black ink is a real placeable fluid with a filled universal bucket.
ItemInkVial exposes a 1000 mB fluid item handler, and machine fluid/item
capabilities permit automation and registered compatible inks. A plain ink
item with correct manual mixer costs does not satisfy this interoperability.
That work is assigned separately; it must be validated on both loaders and
must not be classified as an optional external integration.

`ItemMyGlasses` is the author-specific gag item: it deletes itself from players
whose exact username is not XCompWiz. It has no survival recipe or ordinary
player gameplay. Recreating author-only access is outside the survival core
parity goal.

## Portal behavior

ItemLinking.onPortalCollision forces Maintain Momentum=true and Generate
Platform=false on transient link information, marks it external, and selects
`linking.link-portal`. These overrides must not mutate the stored book. The
previous implementation retained Generate Platform and used the ordinary
link sound; that defect was reported to the linking owner. Original portal
sound is now registered as MystcraftSounds.PORTAL with its original asset.

Third-party IItemPortalActivator, Forge capability, IMC and old event API
compatibility is separate from the built-in book behavior. A port supporting
its own books should not claim arbitrary old-mod item interoperability.

## Configuration and administration

Source defaults include `respawning.respawnInAges=true`,
`teleportation.requireUUIDTest=false`, `teleportation.homedim=0`,
`renderlabels=false`, `generation.villageDeskGen=true`,
`crafting.linkbook.enabled=true`, and configurable symbol/instability balance.
WorldProviderMyst.canRespawnHere directly returns respawnInAges. Login checks
stored dimension identity: absent UUID when strict, a dead dimension, or a
UUID mismatch schedules an eject to the configured home dimension. Namespace
world keys and UUID age identities are appropriate modern replacements, but
these options and their persistence need explicit implementation/validation.

Source LinkListenerPermissions stores four per-username maps in
`mystcraft-linking-permissions`: permit/restrict for entry/departure. Restrict
wins; presence of a permit set makes it a whitelist; missing rules allow
travel. Operator TPX links bypass these checks. The source command is
`/myst-permissions <player> permit|restrict entry|depart <dimension|all>`.
Administrative command/config implementation belongs to the main agent.

The optional book label configuration now has a shared loader-neutral JSON
adapter, separate client/server preferences, login permission transport and
25-block billboard range. Receptacle pitch/yaw persist and render without an
invented interaction gesture. Rendering, native structure version differences
and LookingGlass boundaries are detailed in the companion presentation and
native-structures audit documents.

## Optional legacy integrations

LookingGlass live destination views, Thaumcraft integration, old plugin/IMC
APIs, and third-party link-event listeners must be reported separately from built-in
survival content. The built-in fluid/item automation contract remains a core
requirement even though individual third-party mods are separate integrations.
LookingGlass is not a built-in screenshot stored in the book, and custom
cover-color/texture editing was not found in the pinned core book renderer.
