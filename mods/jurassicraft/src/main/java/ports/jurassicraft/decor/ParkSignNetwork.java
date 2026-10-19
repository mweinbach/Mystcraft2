package ports.jurassicraft.decor;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import ports.jurassicraft.PortMod;

/** Each selection must correspond to a recent, server-observed use of the item. */
public final class ParkSignNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(PortMod.MOD_ID, "paddock_sign"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    private static final Map<Player, Pending> PENDING = new WeakHashMap<>();
    private ParkSignNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(Selection.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Selection::write).decoder(Selection::read)
                .consumerMainThread((message, supplier) -> {
                    ServerPlayer player = supplier.get().getSender();
                    if (player != null) select(player, message.pos(), message.side(), message.hand(), message.artwork());
                    supplier.get().setPacketHandled(true);
                }).add();
    }
    static void begin(Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (!player.level.isClientSide && side.getAxis().isHorizontal())
            PENDING.put(player, new Pending(player.level, pos.immutable(), side, hand, player.level.getGameTime() + 2400));
    }
    public static void sendSelection(BlockPos pos, Direction side, InteractionHand hand, String artwork) {
        CHANNEL.sendToServer(new Selection(pos, side.get3DDataValue(), hand.ordinal(), ParkSigns.PADDOCKS.indexOf(artwork)));
    }
    static boolean select(Player player, BlockPos pos, int sideId, int handId, int artwork) {
        Pending pending = PENDING.remove(player);
        if (pending == null || player.level != pending.level() || player.level.getGameTime() > pending.expires()
                || !pending.pos().equals(pos) || pending.side().get3DDataValue() != sideId || pending.hand().ordinal() != handId
                || artwork < 0 || artwork >= ParkSigns.PADDOCKS.size() || player.isSpectator()) return false;
        Level level = player.level;
        BlockPos support = pos.relative(pending.side().getOpposite());
        ItemStack stack = player.getItemInHand(pending.hand());
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(support) || !level.mayInteract(player, support)
                || !stack.is(DecorRegistry.PADDOCK_SIGN.get()) || !player.mayUseItemAt(pos, pending.side(), stack)
                || player.distanceToSqr(Vec3.atCenterOf(support)) > 64) return false;
        ParkSignEntity sign = new ParkSignEntity(DecorRegistry.PADDOCK_SIGN_ENTITY.get(), level, pos, pending.side());
        sign.setArtwork(ParkSigns.PADDOCKS.get(artwork));
        if (!sign.survives() || !level.addFreshEntity(sign)) return false;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return true;
    }
    private record Pending(Level level, BlockPos pos, Direction side, InteractionHand hand, long expires) {}
    private record Selection(BlockPos pos, int side, int hand, int artwork) {
        void write(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeByte(side); buf.writeByte(hand); buf.writeByte(artwork); }
        static Selection read(FriendlyByteBuf buf) { return new Selection(buf.readBlockPos(), buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte()); }
    }
}
