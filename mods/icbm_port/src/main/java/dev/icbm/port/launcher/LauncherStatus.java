package dev.icbm.port.launcher;

/**
 * Launcher action results, adapted from MIT ICBM-Classic {@code content/blocks/launcher/status/
 * LauncherStatus.java} and {@code FiringWithDelay.java}. Port-only entries: {@link #ERROR_FRAME}
 * (tier frame rule), {@link #ERROR_BLOCKED} (muzzle obstructed) and {@link #ERROR_EMP}.
 */
public enum LauncherStatus {
  READY(false),
  LAUNCHED(false),
  DELAYED(true),
  ERROR_POWER(true),
  ERROR_EMPTY(true),
  ERROR_INVALID_STACK(true),
  ERROR_MIN_RANGE(true),
  ERROR_MAX_RANGE(true),
  ERROR_TARGET(true),
  ERROR_FRAME(true),
  ERROR_BLOCKED(true),
  ERROR_EMP(true),
  ERROR_SPAWN(true),
  ERROR_QUEUED(true);

  private final boolean blocking;

  LauncherStatus(boolean blocking) {
    this.blocking = blocking;
  }

  /** Upstream {@code IActionStatus#isBlocking}: this launcher will not fire now. */
  public boolean blocking() {
    return blocking;
  }

  /** The request was accepted: a missile left the pad or a delayed firing was queued. */
  public boolean accepted() {
    return this == LAUNCHED || this == DELAYED;
  }

  public String translationKey() {
    return "message.icbm_port.launcher.status." + name().toLowerCase(java.util.Locale.ROOT);
  }

  public static LauncherStatus byOrdinal(int ordinal) {
    LauncherStatus[] values = values();
    return ordinal >= 0 && ordinal < values.length ? values[ordinal] : READY;
  }
}
