package net.mystcraft.smoke.forge;

import com.mojang.authlib.GameProfile;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.linking.LinkingService;
import net.mystcraft.minecraft.linking.LinkModifiers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Exercises the actual Forge travel veto and the held-book rollback transaction. */
public final class ForgeTravelVetoSmoke {
    private static final BlockPos ORIGIN = new BlockPos(112, 200, 112);
    private static final BlockPos ARRIVAL = new BlockPos(128, 200, 128);

    private ForgeTravelVetoSmoke() {}

    public static List<String> run(MinecraftServer server, ServerLevel origin) {
        check(server.isSameThread(), "Forge veto smoke must run on the server thread");
        ServerLevel target = server.getLevel(origin.dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER);
        check(target != null && target != origin, "Forge veto requires an actual second dimension");
        Map<ServerLevel, Map<BlockPos, BlockState>> snapshots = new LinkedHashMap<>();
        UUID playerId = UUID.randomUUID();
        String marker = UUID.randomUUID().toString();
        Veto listener = new Veto(playerId);
        ServerPlayer player = null;
        boolean registered = false;
        try {
            preparePad(origin, ORIGIN, snapshots);
            preparePad(target, ARRIVAL, snapshots);
            player = new ServerPlayer(server, origin, new GameProfile(playerId, "MystSmokeVeto"), null);
            Connection connection = new Connection(PacketFlow.SERVERBOUND) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet, PacketSendListener callback) {}
            };
            player.connection = new ServerGamePacketListenerImpl(server, connection, player);
            player.setPos(112.5, 200, 112.5);
            origin.addNewPlayer(player);
            MinecraftForge.EVENT_BUS.register(listener);
            registered = true;
            for (boolean following : new boolean[]{false, true}) {
                listener.calls = 0;
                player.getInventory().clearContent();
                target.setBlock(ARRIVAL.below(), Blocks.AIR.defaultBlockState(), 3);
                target.setBlock(ARRIVAL.below(2), Blocks.AIR.defaultBlockState(), 3);
                ItemStack book = marked(new ItemStack(MystcraftItems.LINKING_BOOK.get()), marker);
                new LinkDestination(target.dimension().location(), 128.5, 200, 128.5, 45, 10).write(book);
                CompoundTag flags = new CompoundTag(); flags.putBoolean(LinkModifiers.DISARM, true);
                flags.putBoolean(LinkModifiers.GENERATE_PLATFORM, true); flags.putBoolean(LinkModifiers.FOLLOWING, following);
                book.getOrCreateTag().put("Flags", flags);
                player.setItemInHand(InteractionHand.MAIN_HAND, book);
                player.getInventory().setItem(1, marked(new ItemStack(Items.DIAMOND, 37), marker));
                player.getInventory().armor.set(2, marked(new ItemStack(Items.IRON_CHESTPLATE), marker));
                player.setItemInHand(InteractionHand.OFF_HAND, marked(new ItemStack(Items.GOLD_INGOT, 8), marker));
                List<ItemStack> before = new ArrayList<>();
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    before.add(player.getInventory().getItem(slot).copy());
                }
                check(!LinkingService.link(player, InteractionHand.MAIN_HAND, book),
                        "Forge travel cancellation must make LinkingService return false");
                check(listener.calls == 1, "one linking attempt must emit exactly one real Forge travel event");
                check(player.getLevel() == origin && player.getX() == 112.5 && player.getY() == 200 && player.getZ() == 112.5,
                        "Forge veto must preserve source dimension and position");
                check(player.getItemInHand(InteractionHand.MAIN_HAND) == book,
                        "Forge veto must preserve the exact original held book");
                for (int slot = 0; slot < before.size(); slot++) check(ItemStack.matches(before.get(slot), player.getInventory().getItem(slot)),
                        "Forge veto must preserve all original inventory, armor and offhand NBT/counts");
                check(target.getBlockState(ARRIVAL.below()).isAir() && target.getBlockState(ARRIVAL.below(2)).isAir(),
                        "Forge veto must revert the provisional one-stone arrival platform");
                check(origin.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(5),
                        entity -> entity.isAlive() && entity.getItem().hasTag()
                                && marker.equals(entity.getItem().getTag().getString("SmokeVeto"))).isEmpty(),
                        "Forge veto must remove all provisional source-book and disarmed inventory entities");
            }
            return List.of("Forge travel veto: one event per attempt, normal/Following Disarm preserves inventory and reverts all drops/platform");
        } finally {
            if (registered) MinecraftForge.EVENT_BUS.unregister(listener);
            if (player != null) player.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            for (ItemEntity entity : origin.getEntitiesOfClass(ItemEntity.class,
                    new AABB(ORIGIN).inflate(5), entity -> entity.getItem().hasTag()
                            && marker.equals(entity.getItem().getTag().getString("SmokeVeto")))) {
                entity.discard();
            }
            for (var level : snapshots.entrySet()) {
                for (var block : level.getValue().entrySet()) level.getKey().setBlock(block.getKey(), block.getValue(), 3);
            }
        }
    }

    private static void preparePad(ServerLevel level, BlockPos center,
                                   Map<ServerLevel, Map<BlockPos, BlockState>> snapshots) {
        Map<BlockPos, BlockState> saved = snapshots.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            BlockPos immutable = pos.immutable();
            check(level.getBlockEntity(immutable) == null, "Forge veto pad must not replace an existing block entity");
            saved.putIfAbsent(immutable, level.getBlockState(immutable));
            level.setBlock(immutable, pos.getY() == center.getY() - 1
                    ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ItemStack marked(ItemStack stack, String marker) {
        stack.getOrCreateTag().putString("SmokeVeto", marker); return stack;
    }

    public static final class Veto {
        private final UUID player;
        private int calls;
        Veto(UUID player) { this.player = player; }
        @SubscribeEvent
        public void onTravel(EntityTravelToDimensionEvent event) {
            if (event.getEntity().getUUID().equals(player)) {
                calls++;
                event.setCanceled(true);
            }
        }
    }
}
