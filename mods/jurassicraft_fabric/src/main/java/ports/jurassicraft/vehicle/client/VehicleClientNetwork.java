package ports.jurassicraft.vehicle.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.vehicle.HelicopterEntity;
import ports.jurassicraft.vehicle.VehicleEntity;
import ports.jurassicraft.vehicle.VehicleNetwork;

/** Client-only radio reception and requests; the server validates every request. */
final class VehicleClientNetwork {
    private VehicleClientNetwork() {}
    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(VehicleNetwork.RADIO_SOUND, (client, handler, buffer, response) -> {
            int entity;
            ItemStack record;
            try { entity = buffer.readVarInt(); record = buffer.readItem(); }
            catch (RuntimeException malformed) { return; }
            if (entity < 0 || buffer.isReadable()) return;
            client.execute(() -> VehicleRadioClient.play(entity, record));
        });
    }
    private static FriendlyByteBuf packet(VehicleEntity vehicle) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(vehicle.getId());
        return buffer;
    }
    static void sendControls(VehicleEntity vehicle, int state) {
        FriendlyByteBuf buffer = packet(vehicle);
        buffer.writeByte(state);
        ClientPlayNetworking.send(VehicleNetwork.CONTROL, buffer);
    }
    static void sendRadioControl(VehicleEntity vehicle) { ClientPlayNetworking.send(VehicleNetwork.RADIO, packet(vehicle)); }
    static void sendSeatControl(VehicleEntity vehicle, int seat) { sendValue(VehicleNetwork.SEAT, vehicle, seat); }
    static void sendFlightMode(HelicopterEntity vehicle, int mode) { sendValue(VehicleNetwork.FLIGHT_MODE, vehicle, mode); }
    private static void sendValue(ResourceLocation channel, VehicleEntity vehicle, int value) {
        FriendlyByteBuf buffer = packet(vehicle);
        buffer.writeVarInt(value);
        ClientPlayNetworking.send(channel, buffer);
    }
}
