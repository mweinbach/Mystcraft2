package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.linking.LinkModifiers;
import net.mystcraft.minecraft.linking.LinkingService;

/** Real-player modifier integration checks; excluded from production artifacts. */
public final class LinkTravelModifierSmoke {
    private static final BlockPos SOURCE = new BlockPos(208, 200, 208);
    private static final BlockPos ARRIVAL = new BlockPos(224, 200, 224);
    private static final String MARKER = "SmokeLinkModifiers";
    private LinkTravelModifierSmoke() {}

    public static List<String> run(MinecraftServer server, ServerLevel origin) {
        check(server.isSameThread(), "link modifier smoke must run on the server thread");
        ServerLevel target = server.getLevel(origin.dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER);
        check(target != null && target != origin, "link modifier smoke requires a second real dimension");
        String marker = UUID.randomUUID().toString();
        Map<ServerLevel, Map<BlockPos, BlockState>> snapshots = new LinkedHashMap<>();
        List<BlockPos> sourcePads = new ArrayList<>(); sourcePads.add(SOURCE);
        ServerPlayer player = null;
        List<String> passed = new ArrayList<>();
        List<String> feedback = new ArrayList<>();
        try {
            pad(origin, SOURCE, snapshots); pad(target, ARRIVAL, snapshots);
            player = player(server, origin, feedback);
            ItemStack intra = book(origin, SOURCE.getX() + 2.5, 200, SOURCE.getZ() + 0.5, marker,
                    LinkModifiers.INTRA_LINKING, LinkModifiers.FOLLOWING);
            player.setItemInHand(InteractionHand.MAIN_HAND, intra);
            player.setDeltaMovement(1, 2, 3); player.fallDistance = 9;
            check(LinkingService.link(player, InteractionHand.MAIN_HAND, intra), "Intra Linking must allow same-dimension travel");
            check(player.getLevel() == origin && player.getX() == SOURCE.getX() + 2.5
                    && player.getItemInHand(InteractionHand.MAIN_HAND) == intra,
                    "Following intra link must retain the exact held book");
            motion(player, new Vec3(0, 0.2, 0));
            check(player.fallDistance == 0 && drops(origin, SOURCE, marker).isEmpty(),
                    "ordinary motion must reset fall distance and Following must create no source book");
            reset(player, origin);
            ItemStack intraOnly = book(target, 224.5, 200, 224.5, marker,
                    LinkModifiers.INTRA_LINKING_ONLY, LinkModifiers.FOLLOWING);
            player.setItemInHand(InteractionHand.MAIN_HAND, intraOnly);
            check(!LinkingService.link(player, InteractionHand.MAIN_HAND, intraOnly)
                    && player.getLevel() == origin && player.getItemInHand(InteractionHand.MAIN_HAND) == intraOnly,
                    "Intra Linking Only must reject cross-dimension travel without consuming book");
            new LinkDestination(origin.dimension().location(), 210.5, 200, 208.5, 90, 0).write(intraOnly);
            check(LinkingService.link(player, InteractionHand.MAIN_HAND, intraOnly),
                    "Intra Linking Only must permit same-dimension travel");
            passed.add("Link modifiers: Intra/IntraOnly permissions, Following retention and default upward nudge");

            reset(player, origin);
            ItemStack momentum = book(target, 224.5, 200, 224.5, marker,
                    LinkModifiers.FOLLOWING, LinkModifiers.MAINTAIN_MOMENTUM);
            player.setItemInHand(InteractionHand.MAIN_HAND, momentum);
            player.setDeltaMovement(0, 0.4, 1); player.fallDistance = 7;
            check(LinkingService.link(player, InteractionHand.MAIN_HAND, momentum), "Maintain Momentum cross-dimension link must succeed");
            motion(player, new Vec3(-1, 0.6, 0));
            check(player.fallDistance == 7 && player.getItemInHand(InteractionHand.MAIN_HAND) == momentum,
                    "Maintain Momentum must retain fall distance and Following book");
            reset(player, origin);
            ItemStack portal = book(target, 224.5, 200, 224.5, marker, LinkModifiers.GENERATE_PLATFORM);
            target.setBlock(ARRIVAL.below(), Blocks.AIR.defaultBlockState(), 3);
            target.setBlock(ARRIVAL.below(2), Blocks.AIR.defaultBlockState(), 3);
            player.setDeltaMovement(0, 0.4, 1); player.fallDistance = 5;
            check(LinkingService.linkFromPortal(player, portal), "portal source must link without a Maintain Momentum flag");
            motion(player, new Vec3(-1, 0.6, 0));
            check(target.getBlockState(ARRIVAL.below()).isAir() && LinkModifiers.from(portal).generatePlatform(),
                    "portal must force Generate Platform off without mutating stored book flag");
            check(player.fallDistance == 5 && LinkModifiers.from(portal).maintainMomentum() == false,
                    "portal forceMaintain must retain motion without modifying source flags");
            passed.add("Link modifiers: classic horizontal momentum redirection, upward nudge and portal forceMaintain");

            reset(player, origin);
            BlockPos originSpawn = origin.getSharedSpawnPos(), targetSpawn = target.getSharedSpawnPos();
            Vec3 relativeDeparture = new Vec3(originSpawn.getX() - 2.7, 201.75, originSpawn.getZ() + 3.9);
            BlockPos relativeSource = new BlockPos(relativeDeparture);
            sourcePads.add(relativeSource); pad(origin, relativeSource, snapshots);
            Vec3 expected = new Vec3(targetSpawn.getX() + (int) (relativeDeparture.x - originSpawn.getX()),
                    targetSpawn.getY() + (int) (relativeDeparture.y - originSpawn.getY()),
                    targetSpawn.getZ() + (int) (relativeDeparture.z - originSpawn.getZ()));
            pad(target, new BlockPos(expected), snapshots);
            player.setPos(relativeDeparture.x, relativeDeparture.y, relativeDeparture.z);
            ItemStack relative = book(target, 10000.5, 200, 10000.5, marker,
                    LinkModifiers.RELATIVE, LinkModifiers.FOLLOWING);
            player.setItemInHand(InteractionHand.MAIN_HAND, relative);
            check(LinkingService.link(player, InteractionHand.MAIN_HAND, relative)
                    && player.position().distanceToSqr(expected) < 1.0E-8,
                    "Relative must use target spawn plus integer-truncated offset from source spawn, ignoring saved coordinates");
            passed.add("Link modifiers: Relative uses exact truncated source-spawn offset, including negative coordinates");

            for (boolean following : new boolean[]{false, true}) {
                reset(player, origin);
                pad(origin, SOURCE, snapshots); pad(target, ARRIVAL, snapshots);
                target.setBlock(ARRIVAL.below(), Blocks.AIR.defaultBlockState(), 3);
                target.setBlock(ARRIVAL.below(2), Blocks.AIR.defaultBlockState(), 3);
                ItemStack disarm = following
                        ? book(target, 224.5, 200, 224.5, marker, LinkModifiers.DISARM,
                                LinkModifiers.GENERATE_PLATFORM, LinkModifiers.FOLLOWING)
                        : book(target, 224.5, 200, 224.5, marker, LinkModifiers.DISARM, LinkModifiers.GENERATE_PLATFORM);
                player.setItemInHand(InteractionHand.MAIN_HAND, disarm);
                player.getInventory().setItem(1, stack(Items.DIAMOND, 37, marker));
                player.getInventory().armor.set(2, stack(Items.IRON_CHESTPLATE, 1, marker));
                player.setItemInHand(InteractionHand.OFF_HAND, stack(Items.GOLD_INGOT, 8, marker));
                feedback.clear();
                check(LinkingService.link(player, InteractionHand.MAIN_HAND, disarm), "Disarm travel must succeed; following="
                        + following + "; feedback=" + feedback + "; source=" + player.getLevel().dimension().location()
                        + "; position=" + player.position() + "; changingDimension=" + player.isChangingDimension()
                        + "; inventory=" + player.getInventory().save(new net.minecraft.nbt.ListTag()));
                check(player.getInventory().isEmpty(), "Disarm must clear main inventory, armor and offhand only after travel");
                List<ItemEntity> dropped = drops(origin, SOURCE, marker);
                check(dropped.stream().allMatch(drop -> drop.saveWithoutId(new CompoundTag()).getShort("PickupDelay") != 32767),
                        "committed Disarm drops must recover their original pickup delay after transaction");
                check(total(dropped, MystcraftItems.LINKING_BOOK.get()) == 1 && total(dropped, Items.DIAMOND) == 37
                        && total(dropped, Items.IRON_CHESTPLATE) == 1 && total(dropped, Items.GOLD_INGOT) == 8,
                        "Disarm must leave exactly one book and the exact original item totals at departure");
                check(target.getBlockState(ARRIVAL.below()).is(Blocks.STONE)
                        && target.getBlockState(ARRIVAL.below(2)).isAir()
                        && target.getBlockState(ARRIVAL.below().east()).is(Blocks.OBSIDIAN),
                        "Generate Platform must create exactly one stone only when both blocks below are air");
                for (ItemEntity drop : dropped) drop.discard();
                passed.add("Link modifiers: " + (following ? "Following+Disarm" : "Disarm")
                        + " preserves source totals without duplicate book; one-stone platform");
            }
            return List.copyOf(passed);
        } finally {
            if (player != null) player.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            for (BlockPos source : sourcePads) for (ItemEntity drop : drops(origin, source, marker)) drop.discard();
            for (var level : snapshots.entrySet()) for (var block : level.getValue().entrySet()) {
                level.getKey().setBlock(block.getKey(), block.getValue(), 3);
            }
        }
    }

    private static ItemStack book(ServerLevel target, double x, double y, double z, String marker, String... flags) {
        ItemStack book = stack(MystcraftItems.LINKING_BOOK.get(), 1, marker);
        new LinkDestination(target.dimension().location(), x, y, z, 90, 0).write(book);
        CompoundTag values = new CompoundTag(); for (String flag : flags) values.putBoolean(flag, true);
        book.getOrCreateTag().put("Flags", values); return book;
    }
    private static ItemStack stack(Item item, int count, String marker) {
        ItemStack stack = new ItemStack(item, count); stack.getOrCreateTag().putString(MARKER, marker); return stack;
    }
    private static List<ItemEntity> drops(ServerLevel level, BlockPos source, String marker) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(source).inflate(7), entity ->
                entity.isAlive() && entity.getItem().hasTag() && marker.equals(entity.getItem().getTag().getString(MARKER)));
    }
    private static int total(List<ItemEntity> entities, Item item) {
        return entities.stream().map(ItemEntity::getItem).filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static void motion(ServerPlayer player, Vec3 expected) {
        check(player.getDeltaMovement().distanceToSqr(expected) < 1.0E-10, "arrival momentum must match independent classic formula expectation");
    }
    private static void reset(ServerPlayer player, ServerLevel origin) {
        player.teleportTo(origin, 208.5, 200, 208.5, 0, 0); player.hasChangedDimension();
        player.getInventory().clearContent(); player.setDeltaMovement(0, 0, 0); player.fallDistance = 0;
    }
    private static ServerPlayer player(MinecraftServer server, ServerLevel level, List<String> feedback) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "MystSmokeFlags"), null);
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) { captureFeedback(packet, feedback); }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { captureFeedback(packet, feedback); }
        };
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        player.setPos(208.5, 200, 208.5); level.addNewPlayer(player); return player;
    }
    private static void captureFeedback(Packet<?> packet, List<String> feedback) {
        // Match by vanilla field type rather than mapped field names on either loader.
        for (java.lang.reflect.Field field : packet.getClass().getDeclaredFields()) {
            if (!Component.class.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
                Component message = (Component) field.get(packet);
                if (message != null) feedback.add(message.toString());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot capture smoke player feedback", error);
            }
        }
    }
    private static void pad(ServerLevel level, BlockPos center, Map<ServerLevel, Map<BlockPos, BlockState>> snapshots) {
        Map<BlockPos, BlockState> saved = snapshots.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 4, 4))) {
            BlockPos copy = pos.immutable(); check(level.getBlockEntity(copy) == null, "modifier smoke cannot replace existing block entities");
            saved.putIfAbsent(copy, level.getBlockState(copy));
            level.setBlock(copy, pos.getY() == center.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
