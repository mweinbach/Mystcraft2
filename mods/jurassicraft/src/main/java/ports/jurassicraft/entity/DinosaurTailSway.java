package ports.jurassicraft.entity;

import net.minecraft.util.Mth;

/**
 * The original tail yaw buffer: {@code calculateChainSwingBuffer(68, 3, 7)} and
 * {@code applyChainSwingBuffer}. Each dinosaur calls it with those constants.
 */
public final class DinosaurTailSway {
    private static final float MAX_ANGLE = 68;
    private static final int BUFFER_TIME = 3;
    private static final float ANGLE_DECREMENT = 7;
    private int yawTimer;
    private float yawVariation;
    private float prevYawVariation;
    private float pausedPartial;

    public void tick(float previousYaw, float yaw) {
        prevYawVariation = yawVariation;
        if (yaw != previousYaw && Mth.abs(yawVariation) < MAX_ANGLE)
            yawVariation += previousYaw - yaw;
        if (yawVariation > .7f * ANGLE_DECREMENT) decay(false);
        else if (yawVariation < -.7f * ANGLE_DECREMENT) decay(true);
    }

    private void decay(boolean negative) {
        if (yawTimer > BUFFER_TIME) {
            yawVariation += negative ? ANGLE_DECREMENT : -ANGLE_DECREMENT;
            if (Mth.abs(yawVariation) < ANGLE_DECREMENT) {
                yawVariation = 0;
                yawTimer = 0;
            }
        } else yawTimer++;
    }

    /** Radians added to every tail cube. Duplicate cubes, as on the microraptor, each receive it. */
    public float radiansPerPart(float partialTick, boolean paused, int parts) {
        if (parts <= 0) return 0;
        float frame = paused ? pausedPartial : partialTick;
        if (!paused) pausedPartial = partialTick;
        return 0.01745329251f * Mth.lerp(frame, prevYawVariation, yawVariation) / parts;
    }

    public static void verify() {
        DinosaurTailSway sway = new DinosaurTailSway();
        sway.tick(0, 10);
        if (sway.yawVariation != -10 || sway.yawTimer != 1)
            throw new AssertionError("turn stores the yaw delta and starts the hold timer");
        sway.tick(10, 10);
        sway.tick(10, 10);
        if (sway.yawVariation != -10 || sway.yawTimer != 3)
            throw new AssertionError("the buffer holds while the timer catches up");
        sway.tick(10, 10);
        if (sway.yawVariation != -10 || sway.yawTimer != 4)
            throw new AssertionError("the hold lasts until the timer passes the buffer time");
        sway.tick(10, 10);
        if (sway.yawVariation != 0 || sway.yawTimer != 0)
            throw new AssertionError("decay that lands inside one step clears the buffer");
        DinosaurTailSway capped = new DinosaurTailSway();
        capped.tick(0, 80);
        float held = capped.yawVariation;
        capped.tick(80, 160);
        if (capped.yawVariation != held) throw new AssertionError("variation past the cap must not keep growing");
        DinosaurTailSway rendered = new DinosaurTailSway();
        rendered.tick(0, 10);
        float radians = rendered.radiansPerPart(1, false, 4);
        float expected = 0.01745329251f * -10 / 4;
        if (Math.abs(radians - expected) > .00001f) throw new AssertionError("tail radians " + radians);
        if (rendered.radiansPerPart(0, true, 4) != radians)
            throw new AssertionError("a paused frame reuses the last partial tick");
    }
}
