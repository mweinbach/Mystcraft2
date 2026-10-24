# Gridwright design guide

Gridwright should feel like a collection of useful machines built from stone, timber, worn iron
and copper, with small crystal components. A player should recognize what a block does from its
shape and working parts. Keep it comfortable beside vanilla Minecraft's buildings and equipment.

The native 32x32 art and compact 3D direction in the
[texture and model study](docs/texture-plan.md) was approved on 2026-10-24. Use it as the baseline
for new work. The study is still an optional preview pack; approval of the direction does not
mean its models, hitboxes or animations have shipped.

## Start with the function

Before drawing a new block, decide what it consumes, what it produces, how the player uses it
and which faces connect to other blocks. Give it a recognizable silhouette and one clear working
part. A mill has a wheel or handle, a drum has hoops and a bung, and a resonator has a crystal.
Use an inset body, plinth, frame or exposed mechanism when it helps explain that function.
Building materials and compressed blocks can remain full cubes.

Extend the nearest existing family before inventing a new casing. Generators share a chassis but
have different fuel symbols. Drums share their body and fittings but have recognizable tiers.
Nodes share their attachment hardware and distinguish items, fluids, energy, pushing and retrieval.
A new material or mechanism can extend these patterns; record the reason and an example here.

## Textures and materials

Author block faces and item sprites at **32x32**, with deliberate pixel clusters and crisp edges.
Use the extra pixels for rivets, short grain, seams, facets and control symbols. Enlarging a 16px
tile does not make a finished 32px texture. Avoid random speckle across an entire face, tiny labels
and broad decorative glow. Keep large surfaces quiet enough for the working part to stand out.

The palette below comes from [the study's drawing source](scripts/texture_study_assets.py).
Reuse these colors for the same materials. Extend the palette when a new material needs it,
while keeping its shading and saturation consistent with the existing set.

| Material or role | Shadow | Base | Highlight | Treatment |
| --- | --- | --- | --- | --- |
| Worn iron | `#555f61` | `#7c8987` | `#aeb9ad` | Plate seams, short scratches and small rivets. |
| Copper | `#725040` | `#b37b50` | `#dfab69` | Fittings, joins and small mechanical details. |
| Stone | `#565b57` | `#85877b` | `#adb09b` | Uneven masonry with a few chipped edges. |
| Timber | `#594332` | `#96754b` | `#c0a16b` | Short grain, plank joints and visible supports. |
| GP crystal | `#28565b` | `#5bb2b0` | `#c0ebcf` | Facets and small crystal marks. |
| FE/TR energy | `#7a4b30` | `#e6a448` | `#ffe1a1` | Bolts, energy indicators and active heat. |
| Fluid | `#294962` | `#5d9dc4` | `#b1d6dd` | Droplets and fluid-specific components. |
| Spatial or arcane component | `#4a3f61` | `#9885b8` | `#e0c8e6` | Small accents on quarries, portals and similar devices. |

Use `#24292d` for deep outlines and `#343a3c` for recesses. Keep the palest edge color,
`#d5d6bb`, on a few readable edges. Fuel and material colors can vary by family; the shape of a
symbol must still identify its meaning without its hue.

Default to opaque pixels and fully transparent cutouts. Treat translucency, emissive rendering
and custom shaders as separate rendering work when a feature needs them. A bright painted pixel
does not emit light. Keep generated art reproducible and commit its drawing source with the PNGs.
When promoting study assets, update the production generators so regeneration preserves the art.

## Shape and construction

Keep models within one block unless a feature deliberately needs a larger footprint. Texture
resolution is 32px; Minecraft model and UV coordinates still use 0..16 units. Start with a small
number of cuboids. The study uses at most 16 per model as a practical starting budget, not an
engine limit. Add geometry where it changes the silhouette or reveals a working component.

Thin panels, open rotors, raised handles and inset machine bodies are preferred examples.
Keep a front face easy to find. Make connection collars and service faces visible. Use tier marks,
different fittings or a changed silhouette to distinguish upgrades; color alone is insufficient.
Item sprites should remain recognizable at inventory display sizes, in the hand and when dropped.
Use a 3D item model when its form benefits from one.

Ship changed geometry with suitable selection and collision shapes, block support rules,
interaction faces and light occlusion. A conservative collision shape can simplify an open wheel,
but it should not leave an invisible full-block barrier around a small machine. Check every facing
and connected variant. Keep inset faces visible beside solid neighbors and give thin parts usable
selection areas. Preserve egg support, drum stacking and safe portal arrival when those shapes change.

Prefer ordinary baked models for static geometry. Add a client renderer for a mechanism that
actually needs motion, such as a rotating mill or hand crank. Drive working animation and indicators
from real machine state. An idle or stalled machine should not appear to be making power.

## Interaction and screens

Use the same symbols in the world, inventory and machine screens. GP uses a crystal mark, FE/TR
energy a bolt, fluid a droplet and items a crate mark. Push and retrieval need different arrow
shapes, and generator fuels need different symbols in both idle and active states.

Show the information that explains what a player should do next: a missing input, full output,
insufficient grid supply, protection refusal or an active job. Reuse existing status text and add
localized strings for new states. A new indicator needs actual data behind it. Static decoration
must not look like a live tank gauge or progress meter.

Keep inventory slots at their menu coordinates. Use readable text, recessed slot wells and a clear
progress direction. Put detailed rates and limits in tooltips when they would crowd the screen.
Test longer translated labels and multiple GUI scales. Pair color with a symbol, text or a distinct
shape so active state, power type and tier remain understandable without color.

## Mechanics that the design must respect

GP is a player/team-owned rate with no storage and no cables. FE/TR energy is a separate stored
resource. Only the deliberate converter bridges them. Do not draw a GP battery or add GP sockets
that imply a cable connection. The converter must show which direction it is converting.

Make inputs, outputs, ownership and machine limits understandable through the model, screen or
tooltip. Add survival recipes and pack integration for new content, and document its costs and
limits. Keep gameplay, registries, assets and state shared across Forge and Fabric. Preserve saved
data and registry IDs when changing appearance.

Gridwright's art and implementation are original. Follow [NOTICE.md](NOTICE.md) and the repository's
clean-room rules. Public player-facing behavior descriptions can inform a mechanic, with sources
recorded in the notice. Do not read or reuse Extra Utilities 2 code, art, models, sounds, language
files, screenshots or unlicensed rewrites to construct an asset.

## Extend and review

For a new family, include a short design note describing its function, silhouette, materials,
symbols and visible states. Name the existing family it extends. Add a representative preview to
the study or the feature's own documentation, then update this guide when the result establishes
a pattern other content should follow. Routine additions can use these defaults directly.

Review the texture at native size and in the game, with the block against stone, oak, grass and
deepslate. Look from every side and at a distance. Include solid neighbors, connected blocks,
stacking, every facing and the applicable idle, active and stalled states. Check the creative tab,
recipe viewers, hand and dropped item. For screens, check GUI scale and translated text.

Use [the study tools](docs/texture-plan.md#review-in-minecraft) for offline comparisons and the preview
pack. Label offline renders as such. Capture actual client views on both loaders before describing
new visuals as verified. Follow the build, parity, EMC and GameTest commands in [README.md](README.md#build-and-test)
and the root contributor instructions. Server tests check behavior; they do not verify appearance.
