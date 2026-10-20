/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.patchouli.component;

import vazkii.patchouli.client.book.page.PageSmelting;

/**
 * One or two smelting recipes, as Patchouli's smelting page shows them. Takes {@code recipe}, {@code recipe2},
 * {@code title} and {@code text}.
 */
public class SmeltingPageComponent
        extends EmbeddedPageComponent<PageSmelting> {

    public SmeltingPageComponent() {
        super(PageSmelting.class);
    }
}
