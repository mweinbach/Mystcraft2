package dev.icbm.port.radio;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;

/**
 * Radio message vocabulary adapted from MIT ICBM-Classic {@code api/radio/messages/} and {@code
 * lib/radio/messages/}: text callbacks, target, trigger, trigger-with-target and incoming-missile
 * messages. Upstream identifies messages by interface ({@code ITargetMessage}, {@code
 * ITriggerActionMessage}); here every message answers {@link #target()} and {@link
 * #shouldTrigger()} so receivers can apply the same capability checks.
 *
 * <p>Channels are integers 1..{@link RadioNetwork#MAX_CHANNEL} instead of upstream's free-form
 * strings; 0 means "unset" (upstream's empty channel) and never transmits.
 */
public sealed interface RadioMessage
    permits RadioMessage.Text,
        RadioMessage.Target,
        RadioMessage.Trigger,
        RadioMessage.TriggerTarget,
        RadioMessage.IncomingMissile {
  int channel();

  /** Target position carried by the message (upstream {@code ITargetMessage#getTarget}). */
  default Vec3d target() {
    return null;
  }

  /** Upstream {@code ITriggerActionMessage#shouldTrigger}; false for non-trigger messages. */
  default boolean shouldTrigger() {
    return false;
  }

  /** Feedback text sent back to a sender (upstream {@code TextMessage}); translation key + args. */
  record Text(int channel, String key, Object... args) implements RadioMessage {
    public Text {
      args = args == null ? new Object[0] : args.clone();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Text text
          && text.channel == channel
          && text.key.equals(key)
          && Arrays.equals(text.args, args);
    }

    @Override
    public int hashCode() {
      return 31 * (31 * channel + key.hashCode()) + Arrays.hashCode(args);
    }

    @Override
    public String toString() {
      return "Text[" + channel + ", " + key + ", " + Arrays.toString(args) + "]";
    }
  }

  /** Upstream {@code TargetMessage}: sets a receiver's target without firing. */
  record Target(int channel, Vec3d target) implements RadioMessage {}

  /** Upstream {@code TriggerActionMessage}: the remote detonator's fire command. */
  record Trigger(int channel) implements RadioMessage {
    @Override
    public boolean shouldTrigger() {
      return true;
    }
  }

  /** Upstream {@code TriggerActionTargetMessage}: the laser detonator's aim-and-fire command. */
  record TriggerTarget(int channel, Vec3d target) implements RadioMessage {
    @Override
    public boolean shouldTrigger() {
      return true;
    }
  }

  /**
   * Upstream {@code IncomingMissileMessage}, broadcast by radar stations. The missile is referenced
   * by UUID (the port never holds a live entity reference in a message); {@link #target()} is the
   * missile position at detection time, as upstream {@code getTarget()} returns the missile's
   * position.
   */
  record IncomingMissile(int channel, UUID missile, Vec3d position, boolean trigger)
      implements RadioMessage {
    @Override
    public Vec3d target() {
      return position;
    }

    @Override
    public boolean shouldTrigger() {
      return trigger;
    }
  }
}
