package com.technovision.advancedgenetics.gametest;

import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.events.KeyInputEvents;
import com.technovision.advancedgenetics.network.KeyPressedPacket;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static com.technovision.advancedgenetics.gametest.GameTestSupport.TEMPLATE;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.check;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.genetics;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.platform;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.player;
import static com.technovision.advancedgenetics.gametest.GameTestSupport.sentGeneticsSyncs;

/** The two packets, on the wire and through each loader's channel, and the events that send genes. */
public class NetworkGameTests {

    @GameTest(template = TEMPLATE)
    public static void geneticsSyncPacketRoundTrips(GameTestHelper helper) {
        CompoundTag genes = genetics(player(helper, "ag_sync_wire", Genes.FLIGHT, Genes.SPEED)).serializeNBT();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncGeneticsPacket.encode(new SyncGeneticsPacket(genes), buf);
        check(SyncGeneticsPacket.decode(buf).genetics().equals(genes) && buf.readableBytes() == 0,
                "Genes changed on the wire: " + genes);

        FriendlyByteBuf none = new FriendlyByteBuf(Unpooled.buffer());
        none.writeNbt(null);
        check(SyncGeneticsPacket.decode(none).genetics().isEmpty(), "A missing compound did not read as no genes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void keyPressedPacketRoundTripsWithinItsLengthLimit(GameTestHelper helper) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        KeyPressedPacket.encode(new KeyPressedPacket(KeyInputEvents.DRAGONS_BREATH), buf);
        check(KeyPressedPacket.decode(buf).geneName().equals(KeyInputEvents.DRAGONS_BREATH) && buf.readableBytes() == 0,
                "The key name changed on the wire");

        String tooLong = "x".repeat(33);
        check(fails(() -> KeyPressedPacket.encode(new KeyPressedPacket(tooLong), new FriendlyByteBuf(Unpooled.buffer()))),
                "An over-long key name was encoded");
        FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
        forged.writeUtf(tooLong);
        check(fails(() -> KeyPressedPacket.decode(forged)), "An over-long key name from a client was accepted");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void keyPressesReachTheServerThroughTheLoaderChannel(GameTestHelper helper) {
        // Handlers hand the press to the server thread, so the result shows up once queued work has run.
        // The press without the gene goes first, so it has been handled once the teleporter's has.
        ServerPlayer without = player(helper, "ag_key_without");
        platform().receiveKeyPress(without, new KeyPressedPacket(KeyInputEvents.TELEPORT));
        ServerPlayer teleporter = player(helper, "ag_key_teleport", Genes.TELEPORT);
        platform().receiveKeyPress(teleporter, new KeyPressedPacket(KeyInputEvents.TELEPORT));
        helper.succeedWhen(() -> {
            check(genetics(teleporter).isOnCooldown(KeyInputEvents.TELEPORT), "The teleport key press did not reach the gene");
            check(!genetics(without).isOnCooldown(KeyInputEvents.TELEPORT), "A player without the Teleport gene used it");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void geneChangesAndLoaderEventsSyncGenes(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ag_sync_events", Genes.FLIGHT);
        sentGeneticsSyncs(player);

        genetics(player).addGene(Genes.SPEED);
        expectOneSync(player, "Adding a gene");
        for (TestPlatform.SyncTrigger trigger : TestPlatform.SyncTrigger.values()) {
            platform().fireSyncTrigger(player, trigger);
            expectOneSync(player, trigger.toString());
        }
        helper.succeed();
    }

    private static void expectOneSync(ServerPlayer player, String cause) {
        List<SyncGeneticsPacket> sent = sentGeneticsSyncs(player);
        check(sent.size() == 1, cause + " sent " + sent.size() + " gene syncs instead of 1");
        CompoundTag expected = genetics(player).serializeNBT();
        check(sent.get(0).genetics().equals(expected), cause + " synced " + sent.get(0).genetics() + " instead of " + expected);
    }

    private static boolean fails(Runnable action) {
        try {
            action.run();
            return false;
        } catch (RuntimeException expected) {
            return true;
        }
    }
}
