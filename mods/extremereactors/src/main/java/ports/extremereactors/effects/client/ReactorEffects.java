/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects.client;

import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.MultiblockReactor;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part.ReactorControlRodEntity;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part.ReactorControllerEntity;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part.ReactorFluidPortEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.data.geometry.CuboidBoundingBox;
import ports.extremereactors.effects.EffectsConfig;
import ports.extremereactors.effects.ReactorAlarm;
import ports.extremereactors.effects.RodDrive;
import ports.extremereactors.effects.ScramDetector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one reactor on the client shows: its rod drives, glow, alarms and SCRAM. It only reads the state the
 * server already sends to the client.
 */
public final class ReactorEffects {

    /** How long a SCRAM flashes the lamps, in ticks. */
    static final int SCRAM_TICKS = 200;
    /** How long a SCRAM vents steam, in ticks. */
    static final int SCRAM_STEAM_TICKS = 40;

    public record Rod(ReactorControlRodEntity entity, Direction outward, RodDrive drive) {
    }

    ReactorEffects(final MultiblockReactor reactor) {
        this._reactor = reactor;
    }

    //region rendering

    public Collection<Rod> getRods() {
        return this._rods.values();
    }

    /**
     * @return the drawn insertion of the control rod at the given position, from 0 to 1, or a negative value if
     * this reactor has no rod there
     */
    public float getRodInsertion(final BlockPos position, final float partialTicks) {

        final Rod rod = this._rods.get(position);

        return null == rod ? -1f : rod.drive().get(partialTicks);
    }

    public int getRodLength() {
        return this._reactor.getFuelRodsLayout().getRodLength();
    }

    public boolean isInteriorVisible() {
        return this._reactor.isInteriorVisible();
    }

    public boolean isPrimaryController(final BlockPos position) {
        return position.equals(this._primaryController);
    }

    /**
     * @return the Cherenkov glow of the whole reactor, from 0 to 1, before the control rods dim each column
     */
    public float getGlow(final float partialTicks) {
        return Mth.lerp(partialTicks, this._previousGlow, this._glow);
    }

    /**
     * @return the RGB color of the controller alarm lamps right now, or -1 if they are dark
     */
    public int getLampColor(final float partialTicks) {

        final float time = this._ticks + partialTicks;

        if (this._scramTicks > 0) {
            return blink(time, 5) ? 0xff2020 : 0xffffff;
        }

        if (ReactorAlarm.anyCritical(this._alarms)) {
            return blink(time, 10) ? 0xff1a1a : -1;
        }

        return this._alarms.isEmpty() || !blink(time, 20) ? -1 : 0xffa000;
    }

    public boolean isScrammed() {
        return this._scramTicks > 0;
    }

    public Set<ReactorAlarm> getAlarms() {
        return Collections.unmodifiableSet(this._alarms);
    }

    //endregion
    //region ticking

    void tick(final Level level, final EffectsConfig config, final RandomSource random) {

        ++this._ticks;
        this._lastTick = level.getGameTime();
        this.refreshParts();

        final MultiblockReactor reactor = this._reactor;
        final boolean active = reactor.isMachineActive();
        boolean allRodsInserted = !this._rods.isEmpty();

        for (final Rod rod : this._rods.values()) {
            allRodsInserted &= rod.entity().getInsertionRatio() >= 100;
        }

        if (this._scramDetector.update(active, allRodsInserted) && config.isOn(config.scram)) {
            this.startScram(config);
        } else if (this._scramTicks > 0) {
            --this._scramTicks;
        }

        final boolean scrammed = this.isScrammed();

        for (final Rod rod : this._rods.values()) {
            rod.drive().tick(rod.entity().getInsertionPercentage(), scrammed);
        }

        final int capacity = reactor.getCapacity();
        final float glowTarget = active && capacity > 0 ?
                Mth.clamp(2f * reactor.getFuelAmount() / capacity, 0f, 1f) : 0f;

        this._previousGlow = this._glow;
        this._glow += (glowTarget - this._glow) * (scrammed ? 0.3f : 0.08f);

        final boolean activelyCooled = reactor.getOperationalMode().isActive();
        final double fuelTemperature = reactor.getFuelHeat().getAsDouble();

        this._alarms = ReactorAlarm.evaluate(new ReactorAlarm.Readings(active, activelyCooled, fuelTemperature,
                        activelyCooled ? reactor.getFluidContainer().getLiquidStoredPercentage() : 1.0,
                        reactor.getFuelAmount(), reactor.getWasteAmount(), capacity),
                config.overheatAlarmTemperature.get(), config.lowCoolantAlarm.get());

        if (config.isOn(config.alarmSounds)) {
            this.updateAlarmSound(config);
        }

        if (config.isOn(config.steam) && !Config.CLIENT.disableReactorParticles.get()) {

            final double density = config.particleDensity.get();

            if (active && activelyCooled && fuelTemperature >= STEAM_TEMPERATURE) {
                this.ventPorts(level, random, density * Mth.clamp(fuelTemperature / 1000.0, 0.2, 1.0) * 0.5);
            }

            if (this._scramTicks > SCRAM_TICKS - SCRAM_STEAM_TICKS) {
                this.ventTop(level, random, density);
            }
        }
    }

    /**
     * Called when this reactor goes out of range or away.
     */
    void discard() {
        this._discarded = true;
    }

    boolean isAlarmSounding() {
        return !this._discarded && this._reactor.isAssembled() &&
                this._reactor.getWorld().getGameTime() - this._lastTick <= 2 &&
                ReactorAlarm.anyCritical(this._alarms) && EffectsConfig.CLIENT.isOn(EffectsConfig.CLIENT.alarmSounds);
    }

    //endregion
    //region internals

    private static final int REFRESH_INTERVAL = 100;
    private static final double STEAM_TEMPERATURE = 100.0;
    private static final int MAX_SCRAM_PUFFS_PER_TICK = 24;

    private record Port(BlockPos position, Direction outward) {
    }

    private static boolean blink(final float time, final int period) {
        return (time % period) < period / 2f;
    }

    /**
     * @return a random whole number of events that averages {@code expected}
     */
    private static int count(final double expected, final RandomSource random) {

        final int whole = (int) expected;

        return whole + (random.nextDouble() < expected - whole ? 1 : 0);
    }

    private void refreshParts() {

        final int partsCount = this._reactor.getPartsCount();

        if (partsCount == this._partsCount && --this._ticksToRefresh > 0) {
            return;
        }

        this._partsCount = partsCount;
        this._ticksToRefresh = REFRESH_INTERVAL;

        final Map<BlockPos, Rod> rods = new HashMap<>();

        this._reactor.getConnectedParts(ReactorControlRodEntity.class).forEach(entity ->
                entity.getOutwardDirection().ifPresent(outward -> {

                    final BlockPos position = entity.getWorldPosition();
                    final Rod known = this._rods.get(position);

                    rods.put(position, null != known && known.entity() == entity ? known :
                            new Rod(entity, outward, new RodDrive(entity.getInsertionPercentage())));
                }));

        this._rods = rods;

        final List<Port> ports = new ArrayList<>();

        this._reactor.getConnectedParts(ReactorFluidPortEntity.class)
                .filter(port -> IoDirection.Output == port.getIoDirection())
                .forEach(port -> port.getOutwardDirection()
                        .ifPresent(outward -> ports.add(new Port(port.getWorldPosition(), outward))));

        this._outputPorts = ports;
        this._primaryController = this._reactor.getConnectedParts(ReactorControllerEntity.class)
                .map(ReactorControllerEntity::getWorldPosition)
                .min(BlockPos::compareTo)
                .orElse(null);
    }

    private void startScram(final EffectsConfig config) {

        this._scramTicks = SCRAM_TICKS;

        if (config.isOn(config.alarmSounds)) {

            final BlockPos where = null != this._primaryController ? this._primaryController :
                    this._reactor.getBoundingBox().getCenter();

            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(MachineEffects.SCRAM,
                    SoundSource.BLOCKS, config.alarmVolume.get().floatValue(), 1f, RandomSource.create(),
                    where.getX() + 0.5, where.getY() + 0.5, where.getZ() + 0.5));
        }
    }

    private void updateAlarmSound(final EffectsConfig config) {

        if (null == this._primaryController || !ReactorAlarm.anyCritical(this._alarms) ||
                (null != this._alarmSound && !this._alarmSound.isStopped())) {
            return;
        }

        this._alarmSound = new AlarmSound(this, this._primaryController, config.alarmVolume.get().floatValue());
        Minecraft.getInstance().getSoundManager().play(this._alarmSound);
    }

    private void ventPorts(final Level level, final RandomSource random, final double perPort) {

        for (final Port port : this._outputPorts) {

            final BlockPos front = port.position().relative(port.outward());

            if (!level.isEmptyBlock(front)) {
                continue;
            }

            for (int puffs = count(perPort, random); puffs > 0; --puffs) {

                final Direction out = port.outward();

                level.addParticle(ParticleTypes.CLOUD,
                        front.getX() + 0.5 - out.getStepX() * 0.45 + (random.nextDouble() - 0.5) * 0.5,
                        front.getY() + 0.5 - out.getStepY() * 0.45 + (random.nextDouble() - 0.5) * 0.5,
                        front.getZ() + 0.5 - out.getStepZ() * 0.45 + (random.nextDouble() - 0.5) * 0.5,
                        out.getStepX() * 0.08, 0.04 + out.getStepY() * 0.08, out.getStepZ() * 0.08);
            }
        }
    }

    private void ventTop(final Level level, final RandomSource random, final double density) {

        final CuboidBoundingBox box = this._reactor.getBoundingBox();
        final int area = box.getLengthX() * box.getLengthZ();
        final BlockPos min = box.getMin();
        final double y = box.getMax().getY() + 1.0;
        final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();

        for (int puffs = Math.min(MAX_SCRAM_PUFFS_PER_TICK, count(density * area / 12.0, random)); puffs > 0; --puffs) {

            final double x = min.getX() + random.nextDouble() * box.getLengthX();
            final double z = min.getZ() + random.nextDouble() * box.getLengthZ();

            if (level.isEmptyBlock(above.set(x, y, z))) {
                level.addParticle(ParticleTypes.CLOUD, x, y, z, (random.nextDouble() - 0.5) * 0.05,
                        0.15 + random.nextDouble() * 0.15, (random.nextDouble() - 0.5) * 0.05);
            }
        }
    }

    private final MultiblockReactor _reactor;
    private final ScramDetector _scramDetector = new ScramDetector();
    private Map<BlockPos, Rod> _rods = Collections.emptyMap();
    private List<Port> _outputPorts = Collections.emptyList();
    private BlockPos _primaryController;
    private int _partsCount = -1;
    private int _ticksToRefresh;
    private int _ticks;
    private long _lastTick;
    private int _scramTicks;
    private float _glow;
    private float _previousGlow;
    private Set<ReactorAlarm> _alarms = Collections.emptySet();
    private AlarmSound _alarmSound;
    private boolean _discarded;

    //endregion
}
