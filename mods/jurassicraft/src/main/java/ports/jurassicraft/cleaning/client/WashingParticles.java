package ports.jurassicraft.cleaning.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import ports.jurassicraft.cleaning.CleaningStationBlock;
import ports.jurassicraft.cleaning.CleaningStationBlockEntity;

/** Spawns the original three-by-four wash spray while the station is cleaning. */
public final class WashingParticles {
    private WashingParticles() {}

    public static void spawn(CleaningStationBlockEntity station) {
        if (!station.visualActive() || !(station.getLevel() instanceof ClientLevel level)) return;
        Direction facing = station.getBlockState().getValue(CleaningStationBlock.FACING);
        int rotation = facing.get2DDataValue();
        double first = 0.5;
        double second = 0.34;
        double opposite = 1.0 - second;
        double velocityX = 0;
        double velocityZ = 0.07;
        boolean inverted = rotation == 1 || rotation == 3;
        if (!inverted) {
            double swap = first;
            first = second;
            second = swap;
            velocityX = velocityZ;
            velocityZ = 0;
        }
        BlockPos pos = station.getBlockPos();
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        double otherX = inverted ? first : opposite;
        double otherZ = inverted ? opposite : second;
        var engine = Minecraft.getInstance().particleEngine;
        for (int i = 0; i < 3; i++) {
            engine.add(new WashingParticle(level, x + first, y + 0.64, z + second, velocityX, 0, velocityZ, rotation));
            engine.add(new WashingParticle(level, x + otherX, y + 0.64, z + otherZ, -velocityX, 0, -velocityZ, rotation));
            engine.add(new WashingParticle(level, x + first, y + 0.6, z + second, velocityX, -0.07, velocityZ, rotation));
            engine.add(new WashingParticle(level, x + otherX, y + 0.6, z + otherZ, -velocityX, -0.07, -velocityZ, rotation));
        }
    }
}
