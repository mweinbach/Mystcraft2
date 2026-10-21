/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.patchouli.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import ports.extremereactors.corelib.internal.Log;
import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.client.book.BookContentsBuilder;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.BookPage;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.client.book.template.TemplateComponent;
import vazkii.patchouli.common.util.SerializationUtil;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * A template component that shows one of Patchouli's own pages (a crafting recipe, a spotlight...) at its
 * position, so that a template can put such a page next to other components. The component's JSON properties,
 * after variable lookup, are the page's properties.
 */
public abstract class EmbeddedPageComponent<P extends BookPage>
        extends TemplateComponent {

    protected EmbeddedPageComponent(final Class<P> pageClass) {
        this._pageClass = pageClass;
    }

    @Nullable
    protected P getPage() {
        return this._page;
    }

    /** Render the page with its origin at the component's position. */
    protected void renderPage(final P page, final PoseStack matrix, final int mouseX, final int mouseY,
                              final float partialTicks) {
        page.render(matrix, mouseX, mouseY, partialTicks);
    }

    //region TemplateComponent

    @Override
    public void onVariablesAvailable(final UnaryOperator<IVariable> lookup) {

        super.onVariablesAvailable(lookup);

        final JsonObject properties = new JsonObject();

        if (null != this.sourceObject) {
            for (final Map.Entry<String, JsonElement> entry : this.sourceObject.entrySet()) {

                if (TEMPLATE_PROPERTIES.contains(entry.getKey())) {
                    continue;
                }

                final JsonElement value = lookup.apply(IVariable.wrap(entry.getValue())).unwrap();

                if (!isBlank(value)) {
                    properties.add(entry.getKey(), value);
                }
            }
        }

        // Patchouli's page adapter wants a page type; the component's own type is not one, so read the page directly
        this._page = SerializationUtil.RAW_GSON.fromJson(properties, this._pageClass);
        this._page.sourceObject = properties;
    }

    @Override
    public void build(final BookContentsBuilder builder, final BookPage page, final BookEntry entry,
                      final int pageNumber) {

        super.build(builder, page, entry, pageNumber);

        if (null != this._page) {
            this._page.build(entry, builder, pageNumber);
        }
    }

    @Override
    public void onDisplayed(final BookPage page, final GuiBookEntry parent, final int left, final int top) {

        super.onDisplayed(page, parent, left, top);

        if (null != this._page) {

            this._page.onDisplayed(parent, left + this.x, top + this.y);
            adoptButtons(page, this._page);
        }
    }

    @Override
    public void render(final PoseStack matrix, final BookPage page, final int mouseX, final int mouseY,
                       final float partialTicks) {

        if (null == this._page) {
            return;
        }

        matrix.pushPose();
        matrix.translate(this.x, this.y, 0);
        this.renderPage(this._page, matrix, mouseX - this.x, mouseY - this.y, partialTicks);
        matrix.popPose();
    }

    @Override
    public boolean mouseClicked(final BookPage page, final double mouseX, final double mouseY, final int button) {
        return null != this._page && this._page.mouseClicked(mouseX - this.x, mouseY - this.y, button);
    }

    //endregion
    //region internals

    private static boolean isBlank(@Nullable final JsonElement value) {
        return null == value || value.isJsonNull() || (value.isJsonPrimitive() && value.getAsString().isEmpty());
    }

    /**
     * Patchouli removes a page's buttons when it hides the page, but it does not know about the embedded page:
     * hand the embedded page's buttons (the multiblock "visualize" one, for example) to the page that holds it.
     */
    @SuppressWarnings("unchecked")
    private static void adoptButtons(final BookPage holder, final BookPage embedded) {

        if (null == s_buttonsField) {
            return;
        }

        try {

            final List<Button> embeddedButtons = (List<Button>) s_buttonsField.get(embedded);
            final List<Button> holderButtons = (List<Button>) s_buttonsField.get(holder);

            if (null != embeddedButtons && null != holderButtons && holder != embedded) {
                holderButtons.addAll(embeddedButtons);
            }

        } catch (final IllegalAccessException | ClassCastException ex) {
            Log.LOGGER.warn("Could not move an embedded handbook page's buttons to its page", ex);
        }
    }

    @Nullable
    private static Field findButtonsField() {

        try {

            final Field field = BookPage.class.getDeclaredField("buttons");

            field.setAccessible(true);
            return field;

        } catch (final NoSuchFieldException | SecurityException ex) {

            Log.LOGGER.warn("Patchouli's page buttons are not accessible: embedded pages may leave their buttons on screen", ex);
            return null;
        }
    }

    private static final Set<String> TEMPLATE_PROPERTIES = Set.of("type", "x", "y", "group", "flag", "advancement",
            "negate_advancement", "guard");
    @Nullable
    private static final Field s_buttonsField = findButtonsField();

    private final transient Class<P> _pageClass;
    @Nullable
    private transient P _page;

    //endregion
}
