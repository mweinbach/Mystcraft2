/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.stack;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.nbt.CompoundTag;

/**
 * Teaches generic containers how to handle one kind of mutable stack (a content plus an amount).
 *
 * @param <Stack> the stack type
 * @param <Content> the type of the thing stacked
 */
public interface IStackAdapter<Stack, Content> {

    Optional<Content> getContent(Stack stack);

    int getAmount(Stack stack);

    /** Changes the amount of a non-empty stack in place and returns it. */
    Stack setAmount(Stack stack, int amount);

    /** Adds {@code delta} to the amount of a non-empty stack in place and returns it. */
    Stack modifyAmount(Stack stack, int delta);

    Stack getEmptyStack();

    boolean isEmpty(Stack stack);

    boolean isStackContentEqual(Stack stack1, Stack stack2);

    boolean isContentEqual(Content content1, Content content2);

    /** Same content, same amount and same extra data. */
    boolean areIdentical(Stack stack1, Stack stack2);

    Stack create(Content content, int amount);

    /** A copy of {@code stack}. */
    Stack create(Stack stack);

    Stack[] createArray(int length);

    List<Stack> createList();

    Set<Stack> createSet();

    Stack readFrom(CompoundTag data);

    CompoundTag writeTo(Stack stack, CompoundTag data);

    String toString(Stack stack);

    <T> T map(Stack stack, Function<Content, T> mapper, T defaultValue);

    <T> T map(Stack stack, IntFunction<T> mapper, T defaultValue);

    <T> T map(Stack stack, BiFunction<Content, Integer, T> mapper, T defaultValue);

    void accept(Stack stack, Consumer<Content> consumer);

    void accept(Stack stack, IntConsumer consumer);

    void accept(Stack stack, BiConsumer<Content, Integer> consumer);
}
