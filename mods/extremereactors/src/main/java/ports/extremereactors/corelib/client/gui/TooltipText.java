/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands the {@code @N} placeholders of tooltip lines.
 * <p>
 * Placeholder N is replaced by the N-th object: a {@link Component} as it is, a {@link Supplier} by what it supplies
 * at the time, anything else by its string form. The replacement keeps the style of the text around it unless it
 * sets its own. Placeholders without an object are left alone.
 */
public final class TooltipText {

    public static List<Component> expand(final List<Component> lines, final List<Object> objects) {

        if (objects.isEmpty()) {
            return lines;
        }

        final List<Component> expanded = new ArrayList<>(lines.size());

        for (final Component line : lines) {
            expanded.add(expand(line, objects));
        }

        return expanded;
    }

    public static Component expand(final Component line, final List<Object> objects) {

        if (objects.isEmpty() || line.getString().indexOf('@') < 0) {
            return line;
        }

        final MutableComponent result = Component.empty();

        line.visit((style, text) -> {

            appendExpanded(result, style, text, objects);
            return Optional.empty();

        }, Style.EMPTY);

        return result;
    }

    //region internals

    private static void appendExpanded(final MutableComponent result, final Style style, final String text,
                                       final List<Object> objects) {

        final Matcher matcher = PLACEHOLDER.matcher(text);
        int start = 0;

        while (matcher.find()) {

            final int index = Integer.parseInt(matcher.group(1));

            if (index >= objects.size()) {
                continue;
            }

            if (matcher.start() > start) {
                result.append(Component.literal(text.substring(start, matcher.start())).withStyle(style));
            }

            result.append(Component.empty().withStyle(style).append(resolve(objects.get(index))));
            start = matcher.end();
        }

        if (start < text.length()) {
            result.append(Component.literal(text.substring(start)).withStyle(style));
        }
    }

    private static Component resolve(final Object object) {

        final Object value = object instanceof Supplier<?> supplier ? supplier.get() : object;

        return value instanceof Component component ? component : Component.literal(String.valueOf(value));
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("@(\\d+)");

    private TooltipText() {
    }

    //endregion
}
