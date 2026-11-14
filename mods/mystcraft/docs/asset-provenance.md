# Imported classic assets

Imported files in the table are byte-identical to the pinned Mystcraft-Legacy revision in `NOTICE.md`. Derived page resources are identified separately below.

Complete machine-readable manifest: [imported-assets.json](imported-assets.json), covering **53 byte-identical imports** (the mod-list icon `icon.png` is a byte-identical copy of the linking-book item texture) with upstream paths and SHA-256 digests. This includes original book covers and pages, binder/mixer/modifier interfaces, desk/collection artwork, glyph atlas, block/entity/item textures, animated portal resources, decay textures, archivist artwork and linking/fissure sounds. The manifest is based on bytes, so renamed modern resource paths retain their original provenance.

## Derived classic symbol-page models

`textures/symbolcomponents.png` is copied unchanged from the official pinned upstream
`textures/symbolcomponents.png`; SHA-256
`16a3e40756c4e6da7be3a122a1a241cc0fc646d628b1de7086e48d0a989e4796`.
The `textures/item/page/*.png` images and corresponding `models/item/page/*.json`
are derived resources, not byte-identical imports. They compose the original
page background and original atlas using `PageBuilder.PageSprite`'s four-word
positions and alpha-to-black component painting. The page background is scaled
5x with nearest-neighbor, the 64px glyphs are placed at `(48,0)`, `(96,48)`,
`(48,96)`, `(0,48)`, then the 160px composition is reduced to 128px with bilinear
sampling. Offline Pillow sampling may differ slightly from Java2D interpolation;
the original component maps and composition layout are retained.

The four-word poems come from `ModSymbols`, `SymbolBlock`, and `SymbolColor`.
`WordData`'s original registered component maps, number components from `ModWords`,
and deterministic lowercase Java string-hash / `java.util.Random` fallback from
`DrawableWordManager` are retained in `classic-symbols.json`, including each page's
resolved poem and glyph indices. Unknown pages use original component 0 (`?`);
link-panel pages use the original black panel rectangle. The atlas/glyphs remain
subject to the retained upstream LGPL license and attribution. Seasonal and addon
custom image sources, and runtime resource-pack recomposition, are not implemented.

`models/item/page.json` selects these models through `mystcraft:page`; the shared
`PageItemModels.value` predicate is installed by each client loader adapter. Logical indices
are 0 blank, 1 unknown, 2 panel, then the catalog's stable `model_value` per symbol.
The actual predicate/JSON threshold is that integer divided by 256, an exact binary
fraction safely inside the standard clamped predicate range.

Regenerate deterministically with Pillow installed:

```sh
python3 mods/mystcraft/tools/generate_symbol_models.py work/mystcraft-legacy
```

The script verifies the supplied official checkout's exact pinned commit before
reading its atlas, word definitions, page background and English labels. The
retained machine-readable catalog supplies the source registration expressions.
It does not run as part of Minecraft startup or either loader build.

The star fissure uses Minecraft’s modern end-portal shader; its original sound is included in the import manifest. New JSON models, blockstates, localization adaptations and generated symbol pages are port-specific derived resources.

## Writing workspace atlas

`gui/writingdesk.png` is copied byte-for-byte from the pinned official legacy asset at the same path. The modern desk uses its original176×166 work panel and58×37 storage-tab fragments; portable collections use its original176×80 inventory fragment. The source30×40 page fragment is reused from the already-imported `gui/bookui_pagel.png`. Shared slot positions retain four visible tabs; the paid-copy recovery output is an explicitly marked modern extra slot.

## Black ink fluid

`textures/block/fluid.png`, `fluid_flow.png` and their animation metadata are unchanged copies of the pinned Mystcraft-Legacy `textures/blocks` resources under the existing LGPL-3.0 attribution. `textures/item/black_ink_bucket_overlay.png` is an unchanged copy of Forge 43.5.0's `assets/forge/textures/item/mask/bucket_fluid.png`, used with the vanilla bucket base and an ink item tint. Its Forge distribution license is retained in `licenses/forge-fluid-mask-LICENSE.txt` (LGPL-2.1). No image editing or generated replacement artwork was used.


## Page surface scrollbar

`gui/scrollbar.png` is copied byte-for-byte from the same pinned official LGPL-3.0 tree. The32×32 atlas supplies the original track caps/repeat and normal/depressed thumb; its digest and upstream path are in [imported-assets.json](imported-assets.json). Square page-browser buttons reuse Minecraft's native `textures/gui/container/stats_icons.png`, as pinned `Assets.Vanilla.slot_tex` does; that vanilla asset is not an additional imported Mystcraft resource.

## Lectern inventory model

`textures/models/lectern_texture.png` is byte-identical to pinned upstream.
`ClassicLecternItemModel` mechanically exports the twelve quads and original
UVs/normals from `models/item/obj/lectern.obj`; it is a derived Java representation,
not replacement artwork. Modern native item transforms adapt the legacy Forge
inventory transform table; exact legacy screen-pixel positioning is not claimed.


## Archivist shop background

`gui/tradeshop.png` is a byte-identical import from the pinned official LGPL-3.0 source. Its SHA-256 and original path appear in [imported-assets.json](imported-assets.json). The shared shop screen retains its 176×181 background and source card/inventory layout, with native keyboard-focusable purchase buttons and server-synchronized stock.

## Agewriter's Guide artwork

`textures/gui/guide_book.png` (300×220) and `textures/item/guide_book.png`
(16×16) are original artwork for this port, distributed under its LGPL-3.0-only
license. They depict teal cloth and leather, brass corner fittings, a stitched
binding, parchment, and a small portal-and-compass emblem. They do not copy
upstream or third-party image assets and are not part of the imported manifest.
The reader background reserves flat, lightly textured areas for readable text.

The images are generated deterministically from the original drawing commands
in `tools/generate_guide_art.py`. With Pillow installed, regenerate them using:

```sh
python3 mods/mystcraft/tools/generate_guide_art.py
```

The generator reads no external artwork and is not required at game runtime or
by either loader build.
