/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.stack;

public enum AllowedHandlerAction {

    ReadOnly(false, false),
    InsertOnly(true, false),
    ExtractOnly(false, true),
    InsertAndExtract(true, true);

    AllowedHandlerAction(final boolean canInsert, final boolean canExtract) {
        this._canInsert = canInsert;
        this._canExtract = canExtract;
    }

    public boolean canInsert() {
        return this._canInsert;
    }

    public boolean canExtract() {
        return this._canExtract;
    }

    private final boolean _canInsert;
    private final boolean _canExtract;
}
