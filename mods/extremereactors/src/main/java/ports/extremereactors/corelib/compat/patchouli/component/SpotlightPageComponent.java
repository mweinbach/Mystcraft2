/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.patchouli.component;

import vazkii.patchouli.client.book.page.PageSpotlight;

/**
 * A framed item with its name and a text, as Patchouli's spotlight page shows them. Takes {@code item},
 * {@code title}, {@code link_recipe} and {@code text}.
 */
public class SpotlightPageComponent
        extends EmbeddedPageComponent<PageSpotlight> {

    public SpotlightPageComponent() {
        super(PageSpotlight.class);
    }
}
