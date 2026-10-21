/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.eventbus.api;

/**
 * The base of the port's events. Each loader adapter fires them from the matching loader event or hook.
 */
public class Event {

    public enum Result {
        DENY,
        DEFAULT,
        ALLOW
    }

    private boolean canceled;
    private Result result = Result.DEFAULT;

    public boolean isCancelable() {
        for (Class<?> type = getClass(); type != Event.class; type = type.getSuperclass()) {
            if (type.isAnnotationPresent(Cancelable.class)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean cancel) {
        if (!isCancelable()) {
            throw new UnsupportedOperationException("Attempted to cancel an uncancelable event: " + getClass().getName());
        }
        canceled = cancel;
    }

    public boolean hasResult() {
        return false;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result value) {
        result = value;
    }
}
