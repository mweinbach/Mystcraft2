package ports.buildcraft.core.marker.volume;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.FriendlyByteBuf;

/** Upstream {@code ClientVolumeBoxes}: the boxes of the client's current level, kept in step by the server. */
public final class ClientVolumeBoxes {
    public static final List<VolumeBox> VOLUME_BOXES = new CopyOnWriteArrayList<>();

    private ClientVolumeBoxes() {}

    /** Upstream {@code MessageVolumeBoxes.HANDLER}: known boxes are updated in place, others added or dropped. */
    public static void receive(FriendlyByteBuf buffer) {
        List<VolumeBox> received = VolumeBoxes.read(buffer);
        VOLUME_BOXES.removeIf(volumeBox -> !received.contains(volumeBox));
        for (VolumeBox volumeBox : received) {
            int index = VOLUME_BOXES.indexOf(volumeBox);
            if (index >= 0) VOLUME_BOXES.get(index).copyFrom(volumeBox);
            else VOLUME_BOXES.add(volumeBox);
        }
    }
}
