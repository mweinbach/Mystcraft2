/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.patchouli.component;

import com.google.gson.annotations.SerializedName;
import vazkii.patchouli.client.book.BookContentsBuilder;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.page.PageCrafting;

import javax.annotation.Nullable;

/**
 * Patchouli's crafting page, with an optional title for each of its two recipes.
 */
public class TitledCraftingPage
        extends PageCrafting {

    @Override
    public void build(final BookEntry entry, final BookContentsBuilder builder, final int pageNumber) {

        super.build(entry, builder, pageNumber);

        if (isSet(this._firstTitle)) {
            this.title1 = this.i18nText(this._firstTitle);
        }

        if (isSet(this._secondTitle)) {
            this.title2 = this.i18nText(this._secondTitle);
        }
    }

    //region internals

    private static boolean isSet(@Nullable final String text) {
        return null != text && !text.isEmpty();
    }

    @Nullable
    @SerializedName("title1")
    private String _firstTitle;
    @Nullable
    @SerializedName("title2")
    private String _secondTitle;

    //endregion
}
