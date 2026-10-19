package ports.jurassicraft.vehicle;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.PortMod;

/** Client input is decoded on the network thread and validated on the server thread. */
public final class VehicleNetwork {
    public static final ResourceLocation CONTROL = id("vehicle_control"), RADIO = id("vehicle_radio"),
            RADIO_SOUND = id("vehicle_radio_sound"), SEAT = id("vehicle_seat"), FLIGHT_MODE = id("vehicle_flight_mode");
    private VehicleNetwork() {}
    private static ResourceLocation id(String path) { return new ResourceLocation(PortMod.MOD_ID, path); }

    public static void init() {
        // Fabric fires this before the spawn packet, so the client queues early events.
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof VehicleEntity vehicle && !vehicle.record().isEmpty())
                sendRecord(player, vehicle, vehicle.record());
        });
        ServerPlayNetworking.registerGlobalReceiver(CONTROL, (server, sender, handler, buffer, response) -> {
            int entity, state;
            try { entity = buffer.readVarInt(); state = buffer.readUnsignedByte(); }
            catch (RuntimeException malformed) { return; }
            if (entity < 0 || buffer.isReadable()) return;
            server.execute(() -> {
                if (sender.level.getEntity(entity) instanceof VehicleEntity vehicle) vehicle.acceptControls(sender, state);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(RADIO, (server, sender, handler, buffer, response) -> {
            int entity;
            try { entity = buffer.readVarInt(); } catch (RuntimeException malformed) { return; }
            if (entity < 0 || buffer.isReadable()) return;
            server.execute(() -> {
                if (sender.level.getEntity(entity) instanceof VehicleEntity vehicle) vehicle.swapRecord(sender);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(SEAT, (server, sender, handler, buffer, response) -> {
            int entity, seat;
            try { entity = buffer.readVarInt(); seat = buffer.readVarInt(); }
            catch (RuntimeException malformed) { return; }
            if (entity < 0 || buffer.isReadable()) return;
            server.execute(() -> {
                if (sender.level.getEntity(entity) instanceof VehicleEntity vehicle) vehicle.changeSeat(sender, seat);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(FLIGHT_MODE, (server, sender, handler, buffer, response) -> {
            int entity, mode;
            try { entity = buffer.readVarInt(); mode = buffer.readVarInt(); }
            catch (RuntimeException malformed) { return; }
            if (entity < 0 || buffer.isReadable()) return;
            server.execute(() -> {
                if (sender.level.getEntity(entity) instanceof HelicopterEntity helicopter) helicopter.toggleFlightMode(sender, mode);
            });
        });
    }
    static void broadcastRecord(VehicleEntity vehicle, ItemStack record) {
        for (ServerPlayer player : PlayerLookup.tracking(vehicle)) sendRecord(player, vehicle, record);
    }
    private static void sendRecord(ServerPlayer player, VehicleEntity vehicle, ItemStack record) {
        if (!ServerPlayNetworking.canSend(player, RADIO_SOUND)) return;
        FriendlyByteBuf packet = PacketByteBufs.create();
        packet.writeVarInt(vehicle.getId());
        packet.writeItem(record);
        ServerPlayNetworking.send(player, RADIO_SOUND, packet);
    }
}
