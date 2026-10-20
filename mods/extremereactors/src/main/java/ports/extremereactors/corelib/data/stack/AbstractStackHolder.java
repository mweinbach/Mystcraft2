/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.stack;

import javax.annotation.Nullable;

/** Listener plumbing shared by the stack holders. */
public abstract class AbstractStackHolder<Holder extends AbstractStackHolder<Holder, Stack>, Stack>
        implements IStackHolder<Holder, Stack> {

    @Override
    public Holder setOnLoadListener(final Runnable listener) {

        this._onLoad = listener;
        return this.self();
    }

    @Override
    public Holder setOnContentsChangedListener(final IChangeListener listener) {

        this._onChange = listener;
        return this.self();
    }

    protected void onLoad() {

        if (null != this._onLoad) {
            this._onLoad.run();
        }
    }

    protected void onChange(final ChangeType changeType, final int index) {

        if (null != this._onChange) {
            this._onChange.onChange(changeType, index);
        }
    }

    /** Works out how a slot changed from its old and new contents. */
    protected static ChangeType changeTypeOf(final boolean wasEmpty, final boolean isEmptyNow, final boolean sameContent) {

        if (wasEmpty) {
            return ChangeType.Added;
        } else if (isEmptyNow) {
            return ChangeType.Removed;
        } else {
            return sameContent ? ChangeType.Modified : ChangeType.Replaced;
        }
    }

    @SuppressWarnings("unchecked")
    private Holder self() {
        return (Holder) this;
    }

    @Nullable
    private Runnable _onLoad;
    @Nullable
    private IChangeListener _onChange;
}
