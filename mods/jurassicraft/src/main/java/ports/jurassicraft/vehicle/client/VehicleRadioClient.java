package ports.jurassicraft.vehicle.client;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import ports.jurassicraft.vehicle.VehicleEntity;

/** Plays record events only on the client, independently from the vehicle engine loop. */
public final class VehicleRadioClient {
    private static final Map<VehicleEntity, RecordSound> PLAYING = new WeakHashMap<>();
    private VehicleRadioClient() {}

    public static void play(int entityId, ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(entityId);
        if (!(entity instanceof VehicleEntity vehicle)) return;
        RecordSound previous = PLAYING.remove(vehicle);
        if (previous != null) client.getSoundManager().stop(previous);
        if (stack.getItem() instanceof RecordItem record) {
            RecordSound sound = new RecordSound(vehicle, record.getSound());
            PLAYING.put(vehicle, sound);
            client.getSoundManager().play(sound);
        }
    }

    private static final class RecordSound extends AbstractTickableSoundInstance {
        private final WeakReference<VehicleEntity> vehicle;
        RecordSound(VehicleEntity entity, SoundEvent event) {
            super(event, SoundSource.RECORDS, RandomSource.create());
            vehicle = new WeakReference<>(entity);
            looping = false;
            x = entity.getX(); y = entity.getY(); z = entity.getZ();
        }
        @Override public void tick() {
            Minecraft client = Minecraft.getInstance();
            VehicleEntity entity = vehicle.get();
            if (entity == null || entity.isRemoved() || entity.level != client.level) { stop(); return; }
            // Upstream moves the source halfway toward the listener for in-car audibility.
            if (client.player != null) {
                x = (entity.getX() + client.player.getX()) / 2;
                y = (entity.getY() + client.player.getY()) / 2;
                z = (entity.getZ() + client.player.getZ()) / 2;
            } else {
                x = entity.getX(); y = entity.getY(); z = entity.getZ();
            }
        }
    }
}
