/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.patchouli.component;

/**
 * One or two crafting recipes, as Patchouli's crafting page shows them. Takes {@code recipe}, {@code recipe2},
 * {@code text} and either {@code title} for both recipes or {@code title1} and {@code title2}.
 */
public class CraftingPageComponent
        extends EmbeddedPageComponent<TitledCraftingPage> {

    public CraftingPageComponent() {
        super(TitledCraftingPage.class);
    }
}
