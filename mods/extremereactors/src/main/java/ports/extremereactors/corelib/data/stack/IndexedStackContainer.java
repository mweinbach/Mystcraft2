/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.stack;

import java.util.EnumMap;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.corelib.IDebugMessages;
import ports.extremereactors.corelib.IDebuggable;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.data.nbt.IMergeableEntity;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;

/**
 * A container with one stack per enum index, e.g. a fuel stack and a waste stack.
 * <p>
 * With a per-index capacity every index can hold up to {@link #getCapacity()} on its own; with a shared capacity the
 * sum of all indices is limited instead. Lowering the capacity never discards contents, it only blocks inserts until
 * there is room again. An index only ever holds one kind of content at a time.
 *
 * @param <Index> the enum naming the slots
 * @param <Content> the type of the thing stored
 * @param <Stack> the mutable stack type, handled through an {@link IStackAdapter}
 */
public class IndexedStackContainer<Index extends Enum<Index>, Content, Stack>
        implements ISyncableEntity, IMergeableEntity, IDebuggable {

    @SafeVarargs
    public IndexedStackContainer(final int capacity, final IStackAdapter<Stack, Content> adapter,
                                 final Index... validIndices) {
        this(capacity, false, 0, adapter, validIndices);
    }

    /**
     * @param sizeHint an expected-workload hint accepted for API compatibility; it does not change behaviour
     */
    @SafeVarargs
    public IndexedStackContainer(final int capacity, final boolean sharedCapacity, final int sizeHint,
                                 final IStackAdapter<Stack, Content> adapter, final Index... validIndices) {

        if (0 == validIndices.length) {
            throw new IllegalArgumentException("At least one index is required");
        }

        this._adapter = adapter;
        this._sharedCapacity = sharedCapacity;
        this._capacity = Math.max(0, capacity);
        this._indices = validIndices.clone();
        this._stacks = new EnumMap<>(validIndices[0].getDeclaringClass());

        for (final Index index : validIndices) {
            this._stacks.put(index, adapter.getEmptyStack());
        }
    }

    //region capacity

    public int getCapacity() {
        return this._capacity;
    }

    public void setCapacity(final int capacity) {
        this._capacity = Math.max(0, capacity);
    }

    public boolean isSharedCapacity() {
        return this._sharedCapacity;
    }

    public int getFreeSpace(final Index index) {

        this.checkIndex(index);
        return Math.max(0, this._capacity - (this._sharedCapacity ? this.getTotalAmount() : this.getContentAmount(index)));
    }

    public int getTotalAmount() {

        int total = 0;

        for (final Index index : this._indices) {
            total += this.getContentAmount(index);
        }

        return total;
    }

    //endregion
    //region contents

    public Optional<Content> getContent(final Index index) {
        return this._adapter.getContent(this.stack(index));
    }

    public int getContentAmount(final Index index) {

        final Stack stack = this.stack(index);

        return this._adapter.isEmpty(stack) ? 0 : this._adapter.getAmount(stack);
    }

    public Stack getStackCopy(final Index index) {

        final Stack stack = this.stack(index);

        return this._adapter.isEmpty(stack) ? this._adapter.getEmptyStack() : this._adapter.create(stack);
    }

    public boolean isEmpty(final Index index) {
        return this._adapter.isEmpty(this.stack(index));
    }

    public boolean isEmpty() {

        for (final Index index : this._indices) {
            if (!this.isEmpty(index)) {
                return false;
            }
        }

        return true;
    }

    public <T> T map(final Index index, final Function<Content, T> mapper, final T defaultValue) {
        return this._adapter.map(this.stack(index), mapper, defaultValue);
    }

    public <T> T map(final Index index, final IntFunction<T> mapper, final T defaultValue) {
        return this._adapter.map(this.stack(index), mapper, defaultValue);
    }

    public <T> T map(final Index index, final BiFunction<Content, Integer, T> mapper, final T defaultValue) {
        return this._adapter.map(this.stack(index), mapper, defaultValue);
    }

    public void accept(final Index index, final Consumer<Content> consumer) {
        this._adapter.accept(this.stack(index), consumer);
    }

    public void accept(final Index index, final IntConsumer consumer) {
        this._adapter.accept(this.stack(index), consumer);
    }

    public void accept(final Index index, final BiConsumer<Content, Integer> consumer) {
        this._adapter.accept(this.stack(index), consumer);
    }

    //endregion
    //region insert / extract

    /** @return the amount accepted, or that would be accepted in {@link OperationMode#Simulate} */
    public int insert(final Index index, final Content content, final int amount, final OperationMode mode) {

        if (amount <= 0 || !this.isContentValidForIndex(index, content)) {
            return 0;
        }

        return this.insertValidated(index, content, amount, mode);
    }

    /** @return the amount accepted, or that would be accepted in {@link OperationMode#Simulate} */
    public int insert(final Index index, final Stack stack, final OperationMode mode) {

        if (this._adapter.isEmpty(stack) || !this.isStackValidForIndex(index, stack)) {
            return 0;
        }

        final Optional<Content> content = this._adapter.getContent(stack);

        return content.map(c -> this.insertValidated(index, c, this._adapter.getAmount(stack), mode)).orElse(0);
    }

    /** @return what was removed, or would be removed in {@link OperationMode#Simulate} */
    public Stack extract(final Index index, final int amount, final OperationMode mode) {

        final Stack current = this.stack(index);

        if (amount <= 0 || this._adapter.isEmpty(current)) {
            return this._adapter.getEmptyStack();
        }

        final int extracted = Math.min(amount, this._adapter.getAmount(current));
        final Stack result = this._adapter.setAmount(this._adapter.create(current), extracted);

        if (mode == OperationMode.Execute) {

            final boolean isEmptyNow = extracted >= this._adapter.getAmount(current);

            if (isEmptyNow) {
                this._stacks.put(index, this._adapter.getEmptyStack());
            } else {
                this._adapter.modifyAmount(current, -extracted);
            }

            this.onExtract(index, isEmptyNow);
        }

        return result;
    }

    /** Like {@link #extract(Enum, int, OperationMode)}, but only if the index holds {@code content}. */
    public Stack extract(final Index index, final Content content, final int amount, final OperationMode mode) {

        final boolean matches = this.getContent(index).map(current -> this._adapter.isContentEqual(current, content)).orElse(false);

        return matches ? this.extract(index, amount, mode) : this._adapter.getEmptyStack();
    }

    /** Empties the index and returns what it held. */
    public Stack clear(final Index index) {

        final Stack current = this.stack(index);

        if (this._adapter.isEmpty(current)) {
            return this._adapter.getEmptyStack();
        }

        this._stacks.put(index, this._adapter.getEmptyStack());
        this.onExtract(index, true);
        return current;
    }

    public void clear() {

        for (final Index index : this._indices) {
            this.clear(index);
        }
    }

    //endregion
    //region validation and notifications

    public boolean isStackValidForIndex(final Index index, final Stack stack) {
        return !this._adapter.isEmpty(stack) && this._adapter.getContent(stack).map(c -> this.isContentValidForIndex(index, c)).orElse(false);
    }

    public boolean isContentValidForIndex(final Index index, final Content content) {
        return true;
    }

    /** Called after an executed insert. */
    protected void onInsert(final Index index, final boolean wasEmpty) {
    }

    /** Called after an executed extract or clear. */
    protected void onExtract(final Index index, final boolean isEmptyNow) {
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {

        if (data.contains("capacity")) {
            this._capacity = Math.max(0, data.getInt("capacity"));
        }

        for (final Index index : this._indices) {

            final String key = index.name();

            this._stacks.put(index, data.contains(key) ? this._adapter.readFrom(data.getCompound(key)) : this._adapter.getEmptyStack());
        }
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {

        data.putInt("capacity", this._capacity);

        for (final Index index : this._indices) {

            final Stack stack = this.stack(index);

            if (!this._adapter.isEmpty(stack)) {
                data.put(index.name(), this._adapter.writeTo(stack, new CompoundTag()));
            }
        }

        return data;
    }

    //endregion
    //region IMergeableEntity

    /** Adds the other container's contents to ours, index by index, where the contents are compatible. */
    @Override
    public void syncDataFrom(final IMergeableEntity other) {

        if (!(other instanceof IndexedStackContainer<?, ?, ?> otherContainer) || otherContainer == this) {
            return;
        }

        for (final Index index : this._indices) {

            if (!otherContainer._stacks.containsKey(index)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            final Stack otherStack = (Stack) otherContainer._stacks.get(index);

            if (this._adapter.isEmpty(otherStack)) {
                continue;
            }

            final Stack current = this.stack(index);

            if (this._adapter.isEmpty(current)) {
                this._stacks.put(index, this._adapter.create(otherStack));
            } else if (this._adapter.isStackContentEqual(current, otherStack)) {
                this._adapter.modifyAmount(current, this._adapter.getAmount(otherStack));
            }
        }
    }

    //endregion
    //region IDebuggable

    @Override
    public void getDebugMessages(final LogicalSide side, final IDebugMessages messages) {

        messages.addUnlocalized("Capacity: %d%s", this._capacity, this._sharedCapacity ? " (shared)" : "");

        for (final Index index : this._indices) {
            messages.addUnlocalized("%s: %s", index.name(), this._adapter.toString(this.stack(index)));
        }
    }

    @Override
    public String toString() {

        final StringBuilder builder = new StringBuilder("capacity ").append(this._capacity);

        for (final Index index : this._indices) {
            builder.append("; ").append(index.name()).append(": ").append(this._adapter.toString(this.stack(index)));
        }

        return builder.toString();
    }

    //endregion
    //region internals

    protected IStackAdapter<Stack, Content> getAdapter() {
        return this._adapter;
    }

    private int insertValidated(final Index index, final Content content, final int amount, final OperationMode mode) {

        final Stack current = this.stack(index);
        final boolean wasEmpty = this._adapter.isEmpty(current);

        if (!wasEmpty && !this._adapter.getContent(current).map(c -> this._adapter.isContentEqual(c, content)).orElse(false)) {
            return 0;
        }

        final int accepted = Math.min(amount, this.getFreeSpace(index));

        if (accepted > 0 && mode == OperationMode.Execute) {

            if (wasEmpty) {
                this._stacks.put(index, this._adapter.create(content, accepted));
            } else {
                this._adapter.modifyAmount(current, accepted);
            }

            this.onInsert(index, wasEmpty);
        }

        return Math.max(0, accepted);
    }

    private Stack stack(final Index index) {

        this.checkIndex(index);
        return this._stacks.get(index);
    }

    private void checkIndex(final Index index) {

        if (!this._stacks.containsKey(index)) {
            throw new IllegalArgumentException("Invalid index: " + index);
        }
    }

    private final IStackAdapter<Stack, Content> _adapter;
    private final boolean _sharedCapacity;
    private final Index[] _indices;
    private final EnumMap<Index, Stack> _stacks;
    private int _capacity;

    //endregion
}
