/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects.client;

import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.MultiblockTurbine;
import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.VentSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.data.geometry.CuboidBoundingBox;
import ports.extremereactors.effects.EffectThresholds;

/**
 * A plume of steam over a turbine that vents its exhaust: always with "vent all", and once its exhaust tank is
 * full with "vent overflow".
 */
final class TurbineEffects {

    static void tick(final MultiblockTurbine turbine, final Level level, final RandomSource random,
                     final double density) {

        final int flow = turbine.getFluidConsumedLastTick();

        if (flow <= 0 || !isVenting(turbine.getVentSetting(), turbine.getFluidContainer().getLiquidStoredPercentage())) {
            return;
        }

        final CuboidBoundingBox box = turbine.getBoundingBox();
        final BlockPos min = box.getMin();
        final double y = box.getMax().getY() + 1.0;
        final double expected = density * Mth.clamp(flow / 500.0, 0.25, 4.0);
        final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();

        for (int puffs = (int) expected + (random.nextDouble() < expected % 1.0 ? 1 : 0); puffs > 0; --puffs) {

            final double x = min.getX() + 0.5 + random.nextDouble() * (box.getLengthX() - 1);
            final double z = min.getZ() + 0.5 + random.nextDouble() * (box.getLengthZ() - 1);

            if (level.isEmptyBlock(above.set(x, y, z))) {
                level.addParticle(ParticleTypes.CLOUD, x, y, z, (random.nextDouble() - 0.5) * 0.04,
                        0.12 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.04);
            }
        }
    }

    static boolean isVenting(final VentSetting setting, final double exhaustShare) {
        return switch (setting) {
            case VentAll -> true;
            case VentOverflow -> exhaustShare >= EffectThresholds.TURBINE_OVERFLOW_SHARE;
            case DoNotVent -> false;
        };
    }

    private TurbineEffects() {
    }
}
