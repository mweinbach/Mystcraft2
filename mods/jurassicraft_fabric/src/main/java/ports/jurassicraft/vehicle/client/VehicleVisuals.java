package ports.jurassicraft.vehicle.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import ports.jurassicraft.vehicle.ExplorerEntity;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleRegistry;
import ports.jurassicraft.vehicle.VehicleSuspension;

/** Client-only presentation derived from server-synchronized positions and occupants. */
final class VehicleVisuals {
    private static final Map<VehicleEntity, State> STATES = new WeakHashMap<>();
    private VehicleVisuals() {}
    static State state(VehicleEntity vehicle) { return STATES.computeIfAbsent(vehicle, ignored -> new State()); }
    static void tick(Minecraft client) {
        if (client.level == null) { STATES.clear(); return; }
        if (client.isPaused()) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof VehicleEntity vehicle) state(vehicle).tick(client, vehicle);
        }
    }
    static final class State {
        private boolean initialized;
        private double x, z;
        private float yaw, previousWheel, wheel, previousSteer, steer, previousRotor, rotor, rotorSpeed;
        private float previousPitch, pitch, previousRoll, roll;
        private float previousGear, gear;
        private final float[] previousDoors = new float[4], doors = new float[4];
        private VehicleEngineSound sound;
        private int soundRetry, warningDelay;
        private float volume;
        float wheel(float partial) { return Mth.lerp(partial, previousWheel, wheel); }
        float steer(float partial) { return Mth.lerp(partial, previousSteer, steer); }
        float rotor(float partial) { return Mth.lerp(partial, previousRotor, rotor); }
        float pitch(float partial) { return Mth.lerp(partial, previousPitch, pitch); }
        float roll(float partial) { return Mth.lerp(partial, previousRoll, roll); }
        float gear(float partial) { return Mth.lerp(partial, previousGear, gear); }
        float door(int seat, float partial) { return Mth.lerp(partial, previousDoors[seat], doors[seat]); }
        float volume() { return volume; }
        private void tick(Minecraft client, VehicleEntity vehicle) {
            double dx = initialized ? vehicle.getX() - x : 0, dz = initialized ? vehicle.getZ() - z : 0;
            float turn = initialized ? Mth.wrapDegrees(vehicle.getYRot() - yaw) : 0;
            x = vehicle.getX(); z = vehicle.getZ(); yaw = vehicle.getYRot(); initialized = true;
            double distance = Math.hypot(dx, dz);
            // Don't spin wheels when an entity receives a large correction or teleports.
            if (distance > 2) dx = dz = distance = 0;
            double theta = Math.toRadians(vehicle.getYRot());
            double forward = -Math.sin(theta) * dx + Math.cos(theta) * dz;
            previousWheel = wheel;
            wheel += (float)(forward / .4);
            if (Math.abs(wheel) > 1000) { float offset = (float)(Math.floor(wheel / (2 * Math.PI)) * 2 * Math.PI); wheel -= offset; previousWheel -= offset; }
            previousSteer = steer;
            steer += (Mth.clamp(-turn * .10F, -.7F, .7F) - steer) * .3F;
            previousRotor = rotor;
            boolean helicopter = vehicle instanceof HelicopterEntity;
            previousPitch = pitch; previousRoll = roll;
            if (!helicopter) {
                VehicleSuspension.Tilt target = VehicleSuspension.sample(vehicle);
                pitch += (target.pitch() - pitch) * .2F;
                roll += (target.roll() - roll) * .2F;
            }
            float targetSpeed = helicopter ? ((HelicopterEntity)vehicle).engineRpm() / 300F : 0;
            rotorSpeed += (targetSpeed - rotorSpeed) * .12F;
            rotor += rotorSpeed;
            if (rotor > 1000) { float offset = (float)(Math.floor(rotor / (2 * Math.PI)) * 2 * Math.PI); rotor -= offset; previousRotor -= offset; }
            updateDoors(client.player, vehicle, helicopter);
            if (helicopter) helicopterEffects(client, (HelicopterEntity)vehicle);
            volume = helicopter ? Math.min(.6F, rotorSpeed * .4F) : (float)Math.min(.7, distance * 2.0);
            if (soundRetry > 0) soundRetry--;
            if (volume > .01F && soundRetry == 0 && (sound == null || sound.isStopped() || !client.getSoundManager().isActive(sound))) {
                sound = new VehicleEngineSound(vehicle, this);
                client.getSoundManager().play(sound);
                soundRetry = 40;
            }
            if (warningDelay > 0) warningDelay--;
            if (helicopter && client.player != null && vehicle.getControllingPassenger() == client.player
                    && vehicle.damageFraction() >= .7F && warningDelay == 0) {
                client.level.playLocalSound(vehicle.getX(), vehicle.getY(), vehicle.getZ(), VehicleRegistry.HELICOPTER_WARNING.get(),
                        SoundSource.NEUTRAL, .15F, 1F, false);
                warningDelay = 17;
            }
        }
        private void helicopterEffects(Minecraft client, HelicopterEntity helicopter) {
            double distance = helicopter.groundDistance();
            previousGear = gear;
            float gearTarget = !helicopter.isOnGround() && distance >= 10 ? -.5F : 0;
            gear = gear < gearTarget ? Math.min(gearTarget, gear + .02F) : Math.max(gearTarget, gear - .02F);
            var random = client.level.random;
            double rpm = helicopter.engineRpm() / 300D;
            double angle = Math.toRadians(helicopter.getYRot());
            if (random.nextDouble() < rpm) {
                for (int side : new int[] {-1, 1}) {
                    double x = helicopter.getX() + Math.cos(angle) * side * .675 + Math.sin(angle) * 3.06;
                    double z = helicopter.getZ() + Math.sin(angle) * side * .675 - Math.cos(angle) * 3.06;
                    client.level.addParticle(ParticleTypes.SMOKE, x, helicopter.getY() + 2.1, z,
                            Math.sin(angle) * .08, .01, -Math.cos(angle) * .08);
                }
            }
            if (random.nextDouble() < Math.max(0, 1.6 * helicopter.damageFraction() - .6)) {
                client.level.addParticle(helicopter.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.LARGE_SMOKE,
                        helicopter.getX(), helicopter.getY() + 2.1, helicopter.getZ(), 0, .05, 0);
                if (!helicopter.isInWater() && helicopter.damageFraction() > .5F && random.nextBoolean())
                    client.level.addParticle(ParticleTypes.FLAME, helicopter.getX(), helicopter.getY() + 2.1, helicopter.getZ(), 0, .02, 0);
            }
            if (distance >= 6 || rpm <= 0) return;
            double power = (1 - distance / 6) * rpm * rpm * rpm;
            var state = client.level.getBlockState(new BlockPos(helicopter.getX(), helicopter.getY() - distance - .05, helicopter.getZ()));
            ParticleOptions particle = state.getFluidState().is(FluidTags.WATER) ? ParticleTypes.SPLASH
                    : state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SOUL_SAND)
                    ? new BlockParticleOption(ParticleTypes.BLOCK, state) : ParticleTypes.CLOUD;
            for (int i = 0; i < 4; i++) {
                if (random.nextDouble() >= power) continue;
                double a = random.nextDouble() * Math.PI * 2, radius = 2.78 * (.9 + random.nextDouble() * .2);
                double x = Math.cos(a) * radius, z = Math.sin(a) * radius;
                client.level.addParticle(particle, helicopter.getX() + x, helicopter.getY() - distance + .1,
                        helicopter.getZ() + z, x / 5, .02, z / 5);
            }
        }
        private void updateDoors(Player player, VehicleEntity vehicle, boolean helicopter) {
            int available = helicopter ? 0 : vehicle instanceof ExplorerEntity ? 4 : 2;
            int nearest = -1;
            double minimum = 16;
            if (player != null && !player.isSpectator() && !vehicle.hasPassenger(player)) {
                for (int i = 0; i < available; i++) {
                    double distance = vehicle.seatPosition(i).distanceToSqr(player.position());
                    if (distance < minimum) { minimum = distance; nearest = i; }
                }
            }
            for (int i = 0; i < doors.length; i++) {
                previousDoors[i] = doors[i];
                float target = i == nearest && vehicle.passengerInSeat(i) == null ? (float)Math.toRadians(i % 2 == 0 ? 60 : -60) : 0;
                doors[i] += (target - doors[i]) * .25F;
            }
        }
    }
}
