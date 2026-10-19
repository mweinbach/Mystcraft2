package ports.jurassicraft.decor;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ports.jurassicraft.platform.Network;
import ports.jurassicraft.platform.Reg;

/** Each selection must correspond to a recent, server-observed use of the item. */
public final class ParkSignNetwork {
    public static final ResourceLocation OPEN = Reg.id("paddock_sign_open");
    public static final ResourceLocation SELECT = Reg.id("paddock_sign_select");
    private static final Map<Player, Pending> PENDING = new WeakHashMap<>();
    private ParkSignNetwork() {}
    public static void register() {
        Network.registerServer(SELECT, (player, buffer) -> {
            BlockPos pos = buffer.readBlockPos();
            int side = buffer.readUnsignedByte(), hand = buffer.readUnsignedByte(), artwork = buffer.readUnsignedByte();
            select(player, pos, side, hand, artwork);
        });
    }
    static void begin(Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (player.level.isClientSide || !side.getAxis().isHorizontal()) return;
        PENDING.put(player, new Pending(player.level.dimension(), pos.immutable(), side, hand, player.level.getGameTime() + 2400));
        if (player instanceof ServerPlayer server) Network.sendToPlayer(server, OPEN,
                buffer -> buffer.writeBlockPos(pos).writeByte(side.get3DDataValue()).writeByte(hand.ordinal()));
    }
    static boolean select(Player player, BlockPos pos, int sideId, int handId, int artwork) {
        Pending pending = PENDING.remove(player);
        if (pending == null || !player.level.dimension().equals(pending.level()) || player.level.getGameTime() > pending.expires()
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
    private record Pending(ResourceKey<Level> level, BlockPos pos, Direction side, InteractionHand hand, long expires) {}
}
