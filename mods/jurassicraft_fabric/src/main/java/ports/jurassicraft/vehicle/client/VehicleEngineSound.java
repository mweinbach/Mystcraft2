package ports.jurassicraft.vehicle.client;

import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleRegistry;

/** The original engine recording, following each visible vehicle without network traffic. */
final class VehicleEngineSound extends AbstractTickableSoundInstance {
    private final WeakReference<VehicleEntity> vehicle;
    private final VehicleVisuals.State state;
    private int quietTicks;
    VehicleEngineSound(VehicleEntity entity, VehicleVisuals.State state) {
        super(VehicleRegistry.CAR_MOVE.get(), SoundSource.NEUTRAL, RandomSource.create());
        this.vehicle = new WeakReference<>(entity);
        this.state = state;
        looping = true;
        delay = 0;
        x = entity.getX(); y = entity.getY(); z = entity.getZ();
        volume = Math.max(.01F, state.volume());
    }
    @Override public void tick() {
        VehicleEntity entity = vehicle.get();
        if (entity == null || entity.isRemoved() || entity.level != Minecraft.getInstance().level) { stop(); return; }
        x = entity.getX(); y = entity.getY(); z = entity.getZ();
        volume = state.volume();
        pitch = .7F + Math.min(1F, volume) * .5F;
        if (volume < .01F) { if (++quietTicks > 40) stop(); }
        else quietTicks = 0;
    }
}
