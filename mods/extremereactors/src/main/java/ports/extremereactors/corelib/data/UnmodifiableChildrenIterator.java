/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;

/** Iterates, read-only, over the children of every parent returned by another iterator. */
public final class UnmodifiableChildrenIterator<Parent, Child> implements Iterator<Child> {

    public UnmodifiableChildrenIterator(final Iterator<Parent> parents, final Function<Parent, Iterator<Child>> children) {

        this._parents = parents;
        this._children = children;
        this._current = Collections.emptyIterator();
    }

    @Override
    public boolean hasNext() {

        while (!this._current.hasNext()) {

            if (!this._parents.hasNext()) {
                return false;
            }

            this._current = this._children.apply(this._parents.next());
        }

        return true;
    }

    @Override
    public Child next() {

        if (!this.hasNext()) {
            throw new NoSuchElementException();
        }

        return this._current.next();
    }

    private final Iterator<Parent> _parents;
    private final Function<Parent, Iterator<Child>> _children;
    private Iterator<Child> _current;
}
