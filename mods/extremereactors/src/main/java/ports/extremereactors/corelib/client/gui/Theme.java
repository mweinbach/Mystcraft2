/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.packs.resources.Resource;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.internal.Log;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * The colours screens paint their controls with.
 * <p>
 * A theme file is a JSON object whose keys are the field names and whose values are {@code RRGGBBAA} hex colours.
 * Missing keys keep the default colour.
 */
public final class Theme {

    public static final Theme DEFAULT = new Theme(Map.of());

    public final Colour FLAT_BACKGROUND_COLOR;
    public final Colour TEXT_ENABLED_COLOR;
    public final Colour TEXT_DISABLED_COLOR;
    public final Colour DARK_OUTLINE_COLOR;

    public final Colour BUTTON_NORMAL_3D_GRADIENT_LIGHT;
    public final Colour BUTTON_NORMAL_3D_GRADIENT_DARK;
    public final Colour BUTTON_NORMAL_3D_BORDER_LIGHT;
    public final Colour BUTTON_NORMAL_3D_BORDER_DARK;

    public final Colour BUTTON_DISABLED_3D_GRADIENT_LIGHT;
    public final Colour BUTTON_DISABLED_3D_GRADIENT_DARK;
    public final Colour BUTTON_DISABLED_3D_BORDER_LIGHT;
    public final Colour BUTTON_DISABLED_3D_BORDER_DARK;

    public final Colour BUTTON_ACTIVE_3D_GRADIENT_LIGHT;
    public final Colour BUTTON_ACTIVE_3D_GRADIENT_DARK;
    public final Colour BUTTON_ACTIVE_3D_BORDER_LIGHT;
    public final Colour BUTTON_ACTIVE_3D_BORDER_DARK;

    public final Colour BUTTON_HIGHLIGHTED_3D_GRADIENT_LIGHT;
    public final Colour BUTTON_HIGHLIGHTED_3D_GRADIENT_DARK;
    public final Colour BUTTON_HIGHLIGHTED_3D_BORDER_LIGHT;
    public final Colour BUTTON_HIGHLIGHTED_3D_BORDER_DARK;

    public final Colour TEXTFIELD_NORMAL_3D_GRADIENT_LIGHT;
    public final Colour TEXTFIELD_NORMAL_3D_GRADIENT_DARK;
    public final Colour TEXTFIELD_NORMAL_3D_BORDER_LIGHT;
    public final Colour TEXTFIELD_NORMAL_3D_BORDER_DARK;
    public final Colour TEXTFIELD_CARET;

    public final Colour GUI_TITLE;

    /** Read a theme file; a broken file gives the default theme. */
    public static Theme read(final Resource resource) {

        try (final Reader reader = resource.openAsReader()) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (final Exception ex) {

            Log.LOGGER.warn("Unable to read GUI theme from {}: {}", resource.sourcePackId(), ex.toString());
            return DEFAULT;
        }
    }

    public static Theme parse(final JsonObject json) {

        final Map<String, Colour> colours = new HashMap<>();

        for (final Map.Entry<String, JsonElement> entry : json.entrySet()) {

            final String value = entry.getValue().getAsString().trim();

            if (8 != value.length()) {
                throw new IllegalArgumentException("Invalid colour " + value + " for " + entry.getKey());
            }

            colours.put(entry.getKey(), Colour.fromRGBA((int) Long.parseLong(value, 16)));
        }

        return new Theme(colours);
    }

    //region internals

    private Theme(final Map<String, Colour> colours) {

        final Lookup c = (key, rgba) -> colours.getOrDefault(key, Colour.fromRGBA(rgba));

        this.FLAT_BACKGROUND_COLOR = c.get("FLAT_BACKGROUND_COLOR", 0x868686FF);
        this.TEXT_ENABLED_COLOR = c.get("TEXT_ENABLED_COLOR", 0x404040FF);
        this.TEXT_DISABLED_COLOR = c.get("TEXT_DISABLED_COLOR", 0xA0A0A0FF);
        this.DARK_OUTLINE_COLOR = c.get("DARK_OUTLINE_COLOR", 0x000000B2);

        this.BUTTON_NORMAL_3D_GRADIENT_LIGHT = c.get("BUTTON_NORMAL_3D_GRADIENT_LIGHT", 0x909090FF);
        this.BUTTON_NORMAL_3D_GRADIENT_DARK = c.get("BUTTON_NORMAL_3D_GRADIENT_DARK", 0x202020FF);
        this.BUTTON_NORMAL_3D_BORDER_LIGHT = c.get("BUTTON_NORMAL_3D_BORDER_LIGHT", 0xDDDDDDFF);
        this.BUTTON_NORMAL_3D_BORDER_DARK = c.get("BUTTON_NORMAL_3D_BORDER_DARK", 0x777777EE);

        this.BUTTON_DISABLED_3D_GRADIENT_LIGHT = c.get("BUTTON_DISABLED_3D_GRADIENT_LIGHT", 0x909090FF);
        this.BUTTON_DISABLED_3D_GRADIENT_DARK = c.get("BUTTON_DISABLED_3D_GRADIENT_DARK", 0x202020FF);
        this.BUTTON_DISABLED_3D_BORDER_LIGHT = c.get("BUTTON_DISABLED_3D_BORDER_LIGHT", 0xDDDDDDFF);
        this.BUTTON_DISABLED_3D_BORDER_DARK = c.get("BUTTON_DISABLED_3D_BORDER_DARK", 0x777777EE);

        this.BUTTON_ACTIVE_3D_GRADIENT_LIGHT = c.get("BUTTON_ACTIVE_3D_GRADIENT_LIGHT", 0x909090FF);
        this.BUTTON_ACTIVE_3D_GRADIENT_DARK = c.get("BUTTON_ACTIVE_3D_GRADIENT_DARK", 0x202020FF);
        this.BUTTON_ACTIVE_3D_BORDER_LIGHT = c.get("BUTTON_ACTIVE_3D_BORDER_LIGHT", 0x777777EE);
        this.BUTTON_ACTIVE_3D_BORDER_DARK = c.get("BUTTON_ACTIVE_3D_BORDER_DARK", 0xDDDDDDFF);

        this.BUTTON_HIGHLIGHTED_3D_GRADIENT_LIGHT = c.get("BUTTON_HIGHLIGHTED_3D_GRADIENT_LIGHT", 0xA0A0A0FF);
        this.BUTTON_HIGHLIGHTED_3D_GRADIENT_DARK = c.get("BUTTON_HIGHLIGHTED_3D_GRADIENT_DARK", 0x303030FF);
        this.BUTTON_HIGHLIGHTED_3D_BORDER_LIGHT = c.get("BUTTON_HIGHLIGHTED_3D_BORDER_LIGHT", 0xDDDDDDFF);
        this.BUTTON_HIGHLIGHTED_3D_BORDER_DARK = c.get("BUTTON_HIGHLIGHTED_3D_BORDER_DARK", 0x777777EE);

        this.TEXTFIELD_NORMAL_3D_GRADIENT_LIGHT = c.get("TEXTFIELD_NORMAL_3D_GRADIENT_LIGHT", 0x909090FF);
        this.TEXTFIELD_NORMAL_3D_GRADIENT_DARK = c.get("TEXTFIELD_NORMAL_3D_GRADIENT_DARK", 0x202020FF);
        this.TEXTFIELD_NORMAL_3D_BORDER_LIGHT = c.get("TEXTFIELD_NORMAL_3D_BORDER_LIGHT", 0xFFFFFFFF);
        this.TEXTFIELD_NORMAL_3D_BORDER_DARK = c.get("TEXTFIELD_NORMAL_3D_BORDER_DARK", 0x2B2B2BFF);
        this.TEXTFIELD_CARET = c.get("TEXTFIELD_CARET", 0x0000006F);

        this.GUI_TITLE = c.get("GUI_TITLE", 0x404040FF);
    }

    @FunctionalInterface
    private interface Lookup {
        Colour get(String key, int defaultRgba);
    }

    //endregion
}
