/*
 * Extreme Reactors 1.19.2 port - tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.test.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import ports.extremereactors.corelib.client.gui.TooltipText;
import ports.extremereactors.corelib.client.gui.control.TextConstraints;
import ports.extremereactors.corelib.client.gui.databind.BindingGroup;
import ports.extremereactors.corelib.client.gui.databind.MonoConsumerBinding;
import ports.extremereactors.corelib.client.gui.databind.MultiConsumerBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data side of the screens: text input constraints, tooltip placeholders and value bindings.
 */
class GuiDataTest {

    @Test
    void positiveIntegerConstraintNormalizesTheText() {

        final var constraint = TextConstraints.CONSTRAINT_POSITIVE_INTEGER_NUMBER;

        assertEquals(Optional.empty(), constraint.apply("1500"));
        assertEquals(Optional.of("0"), constraint.apply(""));
        assertEquals(Optional.of("42"), constraint.apply("0042"));
        assertEquals(Optional.of(Integer.toString(Integer.MAX_VALUE)), constraint.apply("99999999999"));
        assertEquals(Optional.of(Integer.toString(Integer.MAX_VALUE)), constraint.apply("999999999999999999999"));
        assertEquals(Optional.of("0"), constraint.apply("-5"));
    }

    @Test
    void percentageConstraintCapsAtOneHundred() {

        final var constraint = TextConstraints.CONSTRAINT_PERCENTAGE;

        assertEquals(Optional.empty(), constraint.apply("100"));
        assertEquals(Optional.empty(), constraint.apply("7"));
        assertEquals(Optional.of("100"), constraint.apply("250"));
        assertTrue(TextConstraints.FILTER_NUMBERS.test('7'));
        assertFalse(TextConstraints.FILTER_NUMBERS.test('-'));
    }

    @Test
    void tooltipPlaceholdersAreReplacedWhenShownAndKeepTheirStyle() {

        final AtomicInteger amount = new AtomicInteger(10);
        final Supplier<Component> live = () -> Component.literal(amount.get() + " mB");
        final Component line = Component.literal("Fuel: @0 of @1 (@5)").withStyle(ChatFormatting.GOLD);
        final List<Object> objects = List.of(live, 4000);

        assertEquals("Fuel: 10 mB of 4000 (@5)", TooltipText.expand(line, objects).getString());

        amount.set(25);

        final Component expanded = TooltipText.expand(line, objects);
        final List<Style> styles = new ArrayList<>();

        expanded.visit((style, text) -> {

            styles.add(style);
            return Optional.empty();

        }, Style.EMPTY);

        assertEquals("Fuel: 25 mB of 4000 (@5)", expanded.getString());
        assertFalse(styles.isEmpty());
        assertTrue(styles.stream().allMatch(style -> ChatFormatting.GOLD.getColor().equals(style.getColor().getValue())));
        assertEquals(List.of(line), TooltipText.expand(List.of(line), List.of()));
    }

    @Test
    void bindingsOnlyPassOnChangedValues() {

        final int[] source = {1};
        final List<Integer> seen = new ArrayList<>();
        final MonoConsumerBinding<int[], Integer> binding = new MonoConsumerBinding<>(source, s -> s[0], seen::add);

        binding.update();
        binding.update();
        source[0] = 2;
        binding.update();
        binding.update();

        assertEquals(List.of(1, 2), seen);
    }

    @Test
    void bindingGroupUpdatesEveryBindingAndEveryConsumerInOrder() {

        final String[] source = {"idle"};
        final List<String> events = new ArrayList<>();
        final BindingGroup group = new BindingGroup();

        group.addBinding(new MultiConsumerBinding<>(source, s -> s[0],
                value -> events.add("label:" + value), value -> events.add("icon:" + value)));
        group.addBinding(new MonoConsumerBinding<>(source, s -> s[0].length(), value -> events.add("len:" + value)));

        group.update();
        source[0] = "active";
        group.update();
        group.clear();
        source[0] = "off";
        group.update();

        assertEquals(List.of("label:idle", "icon:idle", "len:4", "label:active", "icon:active", "len:6"), events);
    }
}
