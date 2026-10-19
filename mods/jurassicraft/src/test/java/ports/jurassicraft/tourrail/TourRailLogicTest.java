package ports.jurassicraft.tourrail;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Runnable without a Minecraft server; exercises all 20 shapes and slope clearance. */
public final class TourRailLogicTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static int direction(TourRailShape.End end) {
        if (end.z() == -1 && end.x() == 0) return TourRailLayout.N;
        if (end.z() == 1 && end.x() == 0) return TourRailLayout.S;
        if (end.x() == 1 && end.z() == 0) return TourRailLayout.E;
        if (end.x() == -1 && end.z() == 0) return TourRailLayout.W;
        if (end.x() == 1 && end.z() == -1) return TourRailLayout.NE;
        if (end.x() == -1 && end.z() == -1) return TourRailLayout.NW;
        if (end.x() == 1 && end.z() == 1) return TourRailLayout.SE;
        return TourRailLayout.SW;
    }
    public static void main(String[] args) {
        for (TourRailShape shape : TourRailShape.values()) {
            TourRailShape.End a = shape.first(), b = shape.second();
            int mask = direction(a) | direction(b);
            int rise = (a.rise() == 1 ? direction(a) : 0) | (b.rise() == 1 ? direction(b) : 0);
            check(TourRailLayout.choose(mask, rise, TourRailShape.NORTH_SOUTH) == shape, "topology " + shape);
            TourRailShape rotated = shape;
            for (int i = 0; i < 4; i++) rotated = rotated.rotate(Rotation.CLOCKWISE_90);
            check(rotated == shape, "full rotation " + shape);
            for (Mirror mirror : Mirror.values()) check(shape.mirror(mirror).mirror(mirror) == shape, "mirror " + shape);
        }
        for (int i = 0; i <= 1000; i++) {
            double t = i / 1000.0;
            double up = TourRailPath.height(.125, 1.125, t);
            double down = TourRailPath.height(1.125, .125, t);
            if (t > .125) check(up >= 1, "ascending collision clearance at " + t);
            if (t < .875) check(down >= 1, "descending collision clearance at " + t);
            check(Math.abs(up - TourRailPath.height(1.125, .125, 1 - t)) < 1e-9, "reverse symmetry at " + t);
            check(TourRailPath.height(.125, .125, t) == .125, "flat height");
        }
        System.out.println("Tour rail topology, transforms, and slope clearance passed");
    }
}
