## Fabric: custom ingredients and key modifiers

Fabric API 0.77 has no custom ingredient API and stores one key mapping per physical key. The port reads Forge's
ingredient types in `Ingredient.fromJson` and keeps a modifier and conflict context on every `KeyMapping`.

Ingredient JSON:

- `forge:nbt` matches item, damage and share tag. A missing tag only equals a missing tag. A new bow is not
  missing its tag: both loaders write `Damage:0` onto a new damageable stack, so `{Damage:0}` matches an undamaged
  bow and rejects a bow with any other damage.
- `forge:partial_nbt` matches when the stack's tag contains the required tags. Extra tags still match.
- `forge:difference` matches the base unless the subtracted ingredient also matches. It is simple when both children
  are, so a tag minus one item is indexed by item. It is empty only when the base is empty, before tags resolve.
- `forge:intersection` matches when every child matches.
- A JSON array that mixes a custom ingredient with vanilla ones becomes a compound ingredient. A single custom entry
  stays that entry. An array of only vanilla ingredients is left to vanilla.
- An object whose type is `forge:compound` throws. Forge does not accept that type; a compound is an array.
- An unknown type throws instead of being read as an item.
- `ItemStack.of` reads the stack, including a `ForgeCaps` compound split out of the `nbt` object.
- On the network a custom ingredient is a varint `-1`, the type id, then the serializer payload. Vanilla's item list
  is anything else, rewound so the normal reader runs. Partial NBT items travel by name. The format round-trips on one
  loader. Cross-loader multiplayer is not promised.
- There is no tag-invalidation counter. Recipes are built after tags bind, and the shared tests read them then.

The shared GameTests load the white stained-glass painting recipe and the crossbow and trident nucleosynthesizing
recipes, craft blue stained glass into white, leave the subtracted white glass unpainted, craft the tagged bow into a
crossbow, and craft eight white transparent plastic blocks and a black dye into eight black ones. They also round-trip
partial NBT, intersection and a mixed array, and reject an unknown type and `forge:compound`.

Key mappings:

- A press sets down every mapping on that key whose context and modifier are active. A release clears every mapping on
  the key, including one that is no longer active, so a chord cannot stick. Forge 43.5.0 only updates the active ones
  on release. `click` still updates the single vanilla mapping; Forge 43.5.0 does not fan that call out.
- `NONE` stays active in game and in a universal context while another modifier is held, so sneak plus the hand-mode
  key still changes mode in reverse. In a screen, a held modifier blocks an unmodified key, so Shift+N shows the
  description instead of the details key.
- The options string is the key name plus `:SHIFT`, `:CONTROL` or `:ALT`. A file saved before that, whose value is the
  bare default key, keeps the mapping's default modifier. An explicit `:NONE` clears it. Forge would clear the
  modifier whenever the colon is absent.
- The controls screen stores the modifier that is held, and leaves the row selected when the pressed key is itself a
  modifier, so Shift then N can be bound. Reset restores the default key and the default modifier. Binding with the
  mouse keeps the modifier already stored.
- The displayed name uses Forge's `forge.controlsgui` translations. Control matches the command key on macOS.
- `same` reports a conflict when contexts conflict and a modifier matches the other key, or when an in-game context
  shares a key with an unmodified binding. The controls list marks that conflict. Forge 43.5.0 draws a modifier-only
  conflict in gold and any other conflict in red; both are drawn as a conflict here. The binding buttons are not widened.
- Sorting tolerates a category that is not in vanilla's order, which is Mekanism's category.
- These vanilla keys use the in-game conflict context, matching Forge 43.5.0: movement (up, left, down, right, jump,
  sneak, sprint), attack, chat, the player list, command, toggle perspective and smooth camera.
