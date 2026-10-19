package ports.jurassicraft.vehicle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.vehicle.client.VehicleRadioClient;

/** Only validated steering keys travel from the controlling passenger to the server. */
public final class VehicleNetwork {
    private static final String VERSION = "4";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(PortMod.MOD_ID, "vehicle_controls"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    private VehicleNetwork() {}
    public static void register() {
        CHANNEL.messageBuilder(Control.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Control::write).decoder(Control::read)
                .consumerMainThread((message, supplier) -> {
                    ServerPlayer sender = supplier.get().getSender();
                    if (sender != null && sender.level.getEntity(message.entityId()) instanceof VehicleEntity vehicle) {
                        vehicle.acceptControls(sender, message.state());
                    }
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(RadioControl.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RadioControl::write).decoder(RadioControl::read)
                .consumerMainThread((message, supplier) -> {
                    ServerPlayer sender = supplier.get().getSender();
                    if (sender != null && sender.level.getEntity(message.entityId()) instanceof VehicleEntity vehicle) {
                        vehicle.swapRecord(sender);
                    }
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(RadioSound.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RadioSound::write).decoder(RadioSound::read)
                .consumerMainThread((message, supplier) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VehicleRadioClient.play(message.entityId(), message.record()));
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(SeatControl.class, 3, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SeatControl::write).decoder(SeatControl::read)
                .consumerMainThread((message, supplier) -> {
                    ServerPlayer sender = supplier.get().getSender();
                    if (sender != null && sender.level.getEntity(message.entityId()) instanceof VehicleEntity vehicle) {
                        vehicle.changeSeat(sender, message.seat());
                    }
                    supplier.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(FlightMode.class, 4, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FlightMode::write).decoder(FlightMode::read)
                .consumerMainThread((message, supplier) -> {
                    ServerPlayer sender = supplier.get().getSender();
                    if (sender != null && sender.level.getEntity(message.entityId()) instanceof HelicopterEntity helicopter) {
                        helicopter.toggleFlightMode(sender, message.mode());
                    }
                    supplier.get().setPacketHandled(true);
                }).add();
    }
    public static void sendControls(VehicleEntity vehicle, int state) { CHANNEL.sendToServer(new Control(vehicle.getId(), state)); }
    public static void sendRadioControl(VehicleEntity vehicle) { CHANNEL.sendToServer(new RadioControl(vehicle.getId())); }
    public static void sendSeatControl(VehicleEntity vehicle, int seat) { CHANNEL.sendToServer(new SeatControl(vehicle.getId(), seat)); }
    public static void sendFlightMode(HelicopterEntity vehicle, int mode) { CHANNEL.sendToServer(new FlightMode(vehicle.getId(), mode)); }
    static void broadcastRecord(VehicleEntity vehicle, ItemStack record) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> vehicle), new RadioSound(vehicle.getId(), record.copy()));
    }
    private record Control(int entityId, int state) {
        void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeByte(state); }
        static Control read(FriendlyByteBuf buf) { return new Control(buf.readVarInt(), buf.readUnsignedByte()); }
    }
    private record RadioControl(int entityId) {
        void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); }
        static RadioControl read(FriendlyByteBuf buf) { return new RadioControl(buf.readVarInt()); }
    }
    private record RadioSound(int entityId, ItemStack record) {
        void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeItem(record); }
        static RadioSound read(FriendlyByteBuf buf) { return new RadioSound(buf.readVarInt(), buf.readItem()); }
    }
    private record SeatControl(int entityId, int seat) {
        void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeVarInt(seat); }
        static SeatControl read(FriendlyByteBuf buf) { return new SeatControl(buf.readVarInt(), buf.readVarInt()); }
    }
    private record FlightMode(int entityId, int mode) {
        void write(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeVarInt(mode); }
        static FlightMode read(FriendlyByteBuf buf) { return new FlightMode(buf.readVarInt(), buf.readVarInt()); }
    }
}
