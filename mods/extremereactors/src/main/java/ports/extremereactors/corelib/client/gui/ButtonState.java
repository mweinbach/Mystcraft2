/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

/**
 * The visual state of a button: pressed (active) or not, under the mouse (highlighted) or disabled.
 */
public enum ButtonState {

    Default(false, false, false),
    Active(true, false, false),
    DefaultHighlighted(false, true, false),
    ActiveHighlighted(true, true, false),
    DefaultDisabled(false, false, true),
    ActiveDisabled(true, false, true);

    public static ButtonState of(final boolean active, final boolean highlighted, final boolean disabled) {

        if (disabled) {
            return active ? ActiveDisabled : DefaultDisabled;
        }

        if (highlighted) {
            return active ? ActiveHighlighted : DefaultHighlighted;
        }

        return active ? Active : Default;
    }

    public boolean isActive() {
        return this._active;
    }

    public boolean isHighlighted() {
        return this._highlighted;
    }

    public boolean isDisabled() {
        return this._disabled;
    }

    /** The highlighted variant of this state; disabled states stay as they are. */
    public ButtonState getHighlighted() {
        return this._disabled ? this : of(this._active, true, false);
    }

    public ButtonState getDisabled() {
        return of(this._active, false, true);
    }

    /** This state without highlight or disabled flags. */
    public ButtonState getBase() {
        return this._active ? Active : Default;
    }

    ButtonState(final boolean active, final boolean highlighted, final boolean disabled) {

        this._active = active;
        this._highlighted = highlighted;
        this._disabled = disabled;
    }

    private final boolean _active;
    private final boolean _highlighted;
    private final boolean _disabled;
}
