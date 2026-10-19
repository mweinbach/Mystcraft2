package ports.jurassicraft.vehicle;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.Reg;

/** Only validated steering keys travel from the controlling passenger to the server. */
public final class VehicleNetwork {
    public static final ResourceLocation CONTROL = Reg.id("vehicle_control"), RADIO = Reg.id("vehicle_radio"),
            RADIO_SOUND = Reg.id("vehicle_radio_sound"), SEAT = Reg.id("vehicle_seat"), FLIGHT_MODE = Reg.id("vehicle_flight_mode");
    private VehicleNetwork() {}

    public static void register() {
        Network.registerServer(CONTROL, (sender, buffer) -> {
            int entity = buffer.readVarInt(), state = buffer.readUnsignedByte();
            if (entity >= 0 && !buffer.isReadable() && sender.level.getEntity(entity) instanceof VehicleEntity vehicle)
                vehicle.acceptControls(sender, state);
        });
        Network.registerServer(RADIO, (sender, buffer) -> {
            int entity = buffer.readVarInt();
            if (entity >= 0 && !buffer.isReadable() && sender.level.getEntity(entity) instanceof VehicleEntity vehicle)
                vehicle.swapRecord(sender);
        });
        Network.registerServer(SEAT, (sender, buffer) -> {
            int entity = buffer.readVarInt(), seat = buffer.readVarInt();
            if (entity >= 0 && !buffer.isReadable() && sender.level.getEntity(entity) instanceof VehicleEntity vehicle)
                vehicle.changeSeat(sender, seat);
        });
        Network.registerServer(FLIGHT_MODE, (sender, buffer) -> {
            int entity = buffer.readVarInt(), mode = buffer.readVarInt();
            if (entity >= 0 && !buffer.isReadable() && sender.level.getEntity(entity) instanceof HelicopterEntity helicopter)
                helicopter.toggleFlightMode(sender, mode);
        });
    }
    static void broadcastRecord(VehicleEntity vehicle, ItemStack record) {
        ItemStack copy = record.copy();
        Network.sendToTracking(vehicle, RADIO_SOUND, buffer -> buffer.writeVarInt(vehicle.getId()).writeItem(copy));
    }
    static void sendRecord(ServerPlayer player, VehicleEntity vehicle, ItemStack record) {
        ItemStack copy = record.copy();
        Network.sendToPlayer(player, RADIO_SOUND, buffer -> buffer.writeVarInt(vehicle.getId()).writeItem(copy));
    }
}
