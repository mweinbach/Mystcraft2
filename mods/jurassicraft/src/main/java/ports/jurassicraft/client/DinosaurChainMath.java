package ports.jurassicraft.client;

import net.minecraft.util.Mth;

/** LLibrary 1.7.15 chain, bob, and walk formulas used by the species animators. */
public final class DinosaurChainMath {
    private DinosaurChainMath() {}

    /** Carcasses freeze procedural motion; sleep keeps half of it, as AnimatableModel does. */
    public static float movementScale(boolean carcass, boolean sleeping) {
        if (carcass) return 0;
        return sleeping ? .5f : 1;
    }

    public static float chainOffset(double rootOffset, int length) {
        return (float) ((rootOffset * Math.PI) / (2 * length));
    }

    public static float chainRotation(float speed, float degree, float swing, float swingAmount,
                                      float offset, int index, float movementScale) {
        return Mth.cos(swing * (speed * movementScale) + offset * index) * swingAmount * (degree * movementScale);
    }

    /** Bob uses {@link Math#sin}, unlike the cosine lookup used by chain and walk. */
    public static float bob(float speed, float degree, boolean bounce, float swing, float swingAmount, float movementScale) {
        degree *= movementScale;
        speed *= movementScale;
        float wave = (float) (Math.sin(swing * speed) * swingAmount * degree);
        if (bounce) return -Math.abs(wave);
        return wave - swingAmount * degree;
    }

    public static float walk(float speed, float degree, boolean invert, float offset, float weight,
                             float swing, float swingAmount, float movementScale) {
        float rotation = Mth.cos(swing * (speed * movementScale) + offset) * (degree * movementScale) * swingAmount
                + weight * swingAmount;
        return invert ? -rotation : rotation;
    }

    public static void verify() {
        float offset = chainOffset(2, 6);
        float expectedOffset = (float) ((2 * Math.PI) / 12);
        if (offset != expectedOffset) throw new AssertionError("chain offset " + offset);
        float rotation = chainRotation(.1f, .05f, 4, .25f, offset, 3, 1);
        float manual = Mth.cos(4 * .1f + offset * 3) * .25f * .05f;
        if (rotation != manual) throw new AssertionError("chain rotation " + rotation);
        if (chainRotation(.1f, .05f, 4, .25f, offset, 3, 0) != 0)
            throw new AssertionError("a carcass movement scale must zero chain motion");
        if (bob(.5f, 1, false, 0, 1, 1) != -1)
            throw new AssertionError("a resting bob sits one degree below the origin");
        float walked = walk(.1f, .7f, false, 0, .5f, 0, .25f, 1);
        float inverted = walk(.1f, .7f, true, 0, .5f, 0, .25f, 1);
        if (walked != -inverted) throw new AssertionError("walk invert negates the weighted rotation");
        if (Math.abs(walked - .3f) > .02f) throw new AssertionError("walk weight " + walked);
        if (movementScale(true, true) != 0 || movementScale(false, true) != .5f || movementScale(false, false) != 1)
            throw new AssertionError("movement scale");
    }
}
