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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.StarFissureRegistry;
import net.mystcraft.minecraft.age.AgeBookData;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.minecraft.age.AgeSavedData;
import net.mystcraft.minecraft.fissure.StarFissureGenerator;
import net.mystcraft.minecraft.fissure.StarFissureLinker;
import net.mystcraft.minecraft.linking.LinkDestination;
import net.mystcraft.minecraft.writing.PageData;

/** Real Age creation, bounded source generation and natural external player/item return. */
public final class StarFissureSmoke {
    private StarFissureSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel ignored) {
        ServerLevel home = server.overworld();
        BlockPos beforeSpawn = home.getSharedSpawnPos(); float beforeAngle = home.getSharedSpawnAngle();
        Map<BlockPos, BlockState> restored = new LinkedHashMap<>();
        ServerPlayer player = null;
        ServerLevel age = null;
        UUID itemId = null;
        List<String> passed = new ArrayList<>();
        try {
            BlockPos safe = new BlockPos(288, 200, 288);
            for (BlockPos pos : BlockPos.betweenClosed(safe.offset(-1, -1, -1), safe.offset(1, 3, 1))) {
                BlockPos at = pos.immutable();
                check(home.getBlockEntity(at) == null, "fissure smoke home pad must not replace block entities");
                restored.put(at, home.getBlockState(at));
                home.setBlock(at, pos.getY() == safe.getY() - 1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            home.setDefaultSpawnPos(safe, 0);
            player = player(server, home);
            CompoundTag panel = PageData.payload(new ItemStack(MystcraftItems.LINKING_PANEL.get()));
            CompoundTag terrain = new CompoundTag(); PageData.setLegacySymbol(terrain, "mystcraft:terrainvoid");
            CompoundTag fissure = new CompoundTag(); PageData.setLegacySymbol(fissure, "mystcraft:starfissure");
            ItemStack book = AgeBookData.create("Fissure smoke", List.of(panel, terrain, fissure));
            CompoundTag malformedProps = new CompoundTag(); malformedProps.putInt("Seed", 0);
            ItemStack malformedSeed = book.copy(); malformedSeed.getOrCreateTag().put("Props", malformedProps);
            int beforeAges = AgeManager.data(server).entries().size();
            check(!AgeManager.prepare(player, malformedSeed) && AgeManager.data(server).entries().size() == beforeAges
                    && AgeBookData.ageId(malformedSeed).isEmpty(), "mistyped requested seed must reject before allocating an identity");
            CompoundTag props = new CompoundTag(); props.putString("Seed", "0"); book.getOrCreateTag().put("Props", props);
            check(AgeManager.prepare(player, book), "star fissure descriptive book must create a real Age");
            AgeSavedData.Entry entry = AgeManager.data(server).get(AgeBookData.ageId(book).orElseThrow());
            check(entry.seed() == 0, "legacy decimal seed string zero must remain an exact explicit seed");
            age = server.getLevel(LinkDestination.read(book).orElseThrow().dimension());
            check(age != null && entry.state().getBoolean("FissureGenerated"), "Age creation must finish the fissure once");
            BlockPos origin = new BlockPos(entry.state().getInt("FissureX"), entry.state().getInt("FissureY"), entry.state().getInt("FissureZ"));
            check(origin.getY() == age.getMinBuildHeight() && age.getBlockState(origin).is(StarFissureRegistry.STAR_FISSURE.get()),
                    "fissure origin must replace modern minimum-Y floor");
            check(age.isEmptyBlock(origin.above()) && age.isEmptyBlock(new BlockPos(origin.getX(), age.getMaxBuildHeight() - 1, origin.getZ())),
                    "fissure clears only its bounded shaft through the highest legal block");
            age.setBlock(origin.above(10), Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            StarFissureGenerator.ensure(age, entry, AgeManager.data(server));
            check(age.getBlockState(origin.above(10)).is(Blocks.DIAMOND_BLOCK), "completed fissure must not regenerate over later edits");
            // Model an interrupted/first generation with a foreign inventory at the original site.
            BlockPos occupied = origin.above(20);
            age.setBlock(occupied, Blocks.CHEST.defaultBlockState(), 3);
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) age.getBlockEntity(occupied);
            check(chest != null, "collision fixture chest must exist");
            chest.setItem(0, new ItemStack(Items.DIAMOND, 11));
            entry.state().remove("FissureGenerated"); entry.state().remove("FissureColumns");
            StarFissureGenerator.ensure(age, entry, AgeManager.data(server));
            BlockPos alternative = new BlockPos(entry.state().getInt("FissureX"), entry.state().getInt("FissureY"), entry.state().getInt("FissureZ"));
            check(!alternative.equals(origin) && age.getBlockEntity(occupied) == chest
                    && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 11,
                    "foreign inventory collision must choose a different site without touching its inventory");
            check(entry.state().contains("FissureColumns", net.minecraft.nbt.Tag.TAG_LIST), "chosen alternate geometry must be frozen for retry");
            origin = alternative;
            passed.add("star fissure collision: deterministic alternate site preserves foreign chest and its exact inventory");
            passed.add("Age seed: mistyped request allocates nothing; legacy signed decimal zero is honored exactly");
            passed.add("star fissure: real Age symbol generates source-noise floor feature with bounded shaft and once-only persistence marker");

            ItemStack held = new ItemStack(Items.DIAMOND, 7); held.getOrCreateTag().putString("FissureOwned", "retained");
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.teleportTo(age, origin.getX() + .5, origin.getY() + .05, origin.getZ() + .5, 123, 17);
            player.setDeltaMovement(2, -5, 3); player.fallDistance = 50;
            age.getBlockState(origin).entityInside(age, origin, player);
            check(player.getLevel() == home && player.getX() == safe.getX() + .5 && player.getY() == safe.getY()
                    && player.getZ() == safe.getZ() + .5 && player.getYRot() == 123 && player.getXRot() == 17,
                    "natural fissure collision returns to shared Overworld spawn preserving source orientation");
            check(player.getDeltaMovement().equals(new Vec3(0, .2, 0)) && player.fallDistance == 0,
                    "fissure reset momentum must add source-style +.2 vertical velocity");
            check(player.getItemInHand(InteractionHand.MAIN_HAND) == held && held.getCount() == 7,
                    "natural external link must not consume or disarm held items");
            passed.add("star fissure: actual player collision returns home, preserves yaw/pitch and inventory, resets fall and gives +.2 upward motion");

            ItemEntity item = new ItemEntity(age, origin.getX() + .5, origin.getY() + .05, origin.getZ() + .5, new ItemStack(Items.EMERALD, 3));
            itemId = item.getUUID(); check(age.addFreshEntity(item), "item fixture must enter Age");
            check(StarFissureLinker.returnHome(item), "natural fissure supports nonplayer entities without vanilla portal creation");
            Entity returned = home.getEntity(itemId);
            check(returned instanceof ItemEntity result && result.getItem().is(Items.EMERALD) && result.getItem().getCount() == 3
                    && returned.getDeltaMovement().equals(new Vec3(0, .2, 0)), "nonplayer transfer retains item payload and gives source momentum");
            home.setDefaultSpawnPos(new BlockPos(safe.getX(), home.getMaxBuildHeight() + 4, safe.getZ()), 0);
            check(StarFissureLinker.safeSpawn(home, returned) == null, "out-of-height home spawn must reject without clearing blocks");
            passed.add("star fissure: exact nonplayer item return preserves stack and unsafe home spawn rejects without destructive clearing");
            return passed;
        } finally {
            if (player != null) player.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            if (itemId != null) {
                Entity inHome = home.getEntity(itemId); if (inHome != null) inHome.discard();
                Entity inAge = age == null ? null : age.getEntity(itemId); if (inAge != null) inAge.discard();
            }
            home.setDefaultSpawnPos(beforeSpawn, beforeAngle);
            restored.forEach((pos, state) -> home.setBlock(pos, state, 3));
        }
    }
    private static ServerPlayer player(MinecraftServer server, ServerLevel level) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "MystSmokeFissure"), null);
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener callback) {}
        };
        player.connection = new ServerGamePacketListenerImpl(server, connection, player);
        player.setPos(288.5, 200, 288.5); level.addNewPlayer(player); return player;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
