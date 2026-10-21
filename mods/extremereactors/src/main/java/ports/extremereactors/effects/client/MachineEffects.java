/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects.client;

import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.MultiblockReactor;
import it.zerono.mods.extremereactors.gamecontent.multiblock.turbine.MultiblockTurbine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ports.extremereactors.corelib.base.multiblock.AbstractMultiblockController;
import ports.extremereactors.effects.EffectsConfig;
import ports.extremereactors.effects.MachineStateCache;
import ports.extremereactors.platform.Platform;

import javax.annotation.Nullable;

/**
 * Entry point of the client-side machine effects. They only read what the server already sends to clients and
 * change nothing in how machines run.
 */
public final class MachineEffects {

    static final SoundEvent ALARM = new SoundEvent(ExtremeReactors.newID("effects.alarm"));
    static final SoundEvent SCRAM = new SoundEvent(ExtremeReactors.newID("effects.scram"));

    /**
     * Load the client options and hook the effects that are turned on. Call once on the physical client, after
     * the block entity types are registered.
     */
    public static void initialize() {

        EffectsConfig.load();

        final EffectsConfig config = EffectsConfig.CLIENT;

        if (!config.enabled.get()) {
            return;
        }

        AbstractMultiblockController.setClientTickListener(MachineEffects::tick);
        Platform.backend().onLevelTickStart(level -> {
            if (level.isClientSide()) {
                s_reactors.sweep(level.getGameTime(), ReactorEffects::discard);
            }
        });
        Platform.backend().onLevelUnload(level -> {
            if (level.isClientSide()) {
                s_reactors.removeIf(reactor -> reactor.getWorld() == level, ReactorEffects::discard);
            }
        });

        if (config.isOn(config.rodDrives)) {
            BlockEntityRenderers.register(Content.TileEntityTypes.REACTOR_CONTROLROD.get(), ControlRodDriveRenderer::new);
        }

        if (config.isOn(config.cherenkovGlow) || config.isOn(config.alarmLamps)) {
            BlockEntityRenderers.register(Content.TileEntityTypes.REACTOR_CONTROLLER.get(), ReactorControllerRenderer::new);
        }
    }

    /**
     * @return the effects of the given reactor, or null if it is out of range or has not ticked on this client yet
     */
    @Nullable
    public static ReactorEffects get(final MultiblockReactor reactor) {
        return s_reactors.get(reactor);
    }

    //region internals

    private static void tick(final AbstractMultiblockController<?, ?> controller) {

        final Level level = controller.getWorld();
        final EffectsConfig config = EffectsConfig.CLIENT;

        if (controller instanceof MultiblockReactor reactor) {

            if (isInRange(controller, config)) {
                s_reactors.tick(reactor, level.getGameTime(), ReactorEffects::new).tick(level, config, level.getRandom());
            } else {

                final ReactorEffects effects = s_reactors.remove(reactor);

                if (null != effects) {
                    effects.discard();
                }
            }

        } else if (controller instanceof MultiblockTurbine turbine && config.isOn(config.steam) &&
                !Config.CLIENT.disableTurbineParticles.get() && isInRange(controller, config)) {
            TurbineEffects.tick(turbine, level, level.getRandom(), config.particleDensity.get());
        }
    }

    private static boolean isInRange(final AbstractMultiblockController<?, ?> controller, final EffectsConfig config) {

        final AABB box = controller.getBoundingBox().getAABB();
        final Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        final double dx = Math.max(0, Math.max(box.minX - eye.x, eye.x - box.maxX));
        final double dy = Math.max(0, Math.max(box.minY - eye.y, eye.y - box.maxY));
        final double dz = Math.max(0, Math.max(box.minZ - eye.z, eye.z - box.maxZ));
        final double range = config.range.get();

        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    private static final MachineStateCache<MultiblockReactor, ReactorEffects> s_reactors = new MachineStateCache<>();

    private MachineEffects() {
    }

    //endregion
}
