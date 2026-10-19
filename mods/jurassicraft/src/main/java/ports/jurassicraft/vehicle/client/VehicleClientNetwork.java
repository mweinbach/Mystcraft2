package ports.jurassicraft.vehicle.client;

import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.client.ClientPlatform;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleNetwork;

/** Client-only radio reception and requests; the server validates every request. */
final class VehicleClientNetwork {
    private VehicleClientNetwork() {}
    static void register() {
        Network.registerClient(VehicleNetwork.RADIO_SOUND, buffer -> {
            int entity = buffer.readVarInt();
            ItemStack record = buffer.readItem();
            if (entity >= 0 && !buffer.isReadable()) VehicleRadioClient.play(entity, record);
        });
    }
    static void sendControls(VehicleEntity vehicle, int state) { send(VehicleNetwork.CONTROL, vehicle, buffer -> buffer.writeByte(state)); }
    static void sendRadioControl(VehicleEntity vehicle) { send(VehicleNetwork.RADIO, vehicle, buffer -> {}); }
    static void sendSeatControl(VehicleEntity vehicle, int seat) { send(VehicleNetwork.SEAT, vehicle, buffer -> buffer.writeVarInt(seat)); }
    static void sendFlightMode(HelicopterEntity vehicle, int mode) { send(VehicleNetwork.FLIGHT_MODE, vehicle, buffer -> buffer.writeVarInt(mode)); }
    private static void send(ResourceLocation channel, VehicleEntity vehicle, Consumer<FriendlyByteBuf> body) {
        ClientPlatform.sendToServer(channel, buffer -> { buffer.writeVarInt(vehicle.getId()); body.accept(buffer); });
    }
}
