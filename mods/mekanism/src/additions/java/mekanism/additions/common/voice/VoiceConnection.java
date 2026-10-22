package mekanism.additions.common.voice;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.UUID;
import mekanism.additions.common.MekanismAdditions;
import mekanism.additions.common.item.ItemWalkieTalkie;
import mekanism.common.Mekanism;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

public class VoiceConnection extends Thread {

    private final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    private DataOutputStream output;
    private DataInputStream input;
    private boolean open = true;
    private final Socket socket;
    private volatile UUID uuid;

    public VoiceConnection(Socket s) {
        socket = s;
    }

    @Override
    public void run() {
        try {
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            VoiceServerManager voiceManager = MekanismAdditions.voiceManager;
            if (voiceManager == null) {
                Mekanism.logger.error("VoiceServer: Voice server is not running.");
                open = false;
                socket.close();
                return;
            }
            // The first 16 bytes are the one-time session token issued over the game connection.
            socket.setSoTimeout(10_000);
            UUID token = new UUID(input.readLong(), input.readLong());
            socket.setSoTimeout(0);
            uuid = voiceManager.claimToken(token);
            if (uuid == null) {
                Mekanism.logger.error("VoiceServer: Voice connection presented no live session.");
                kill();
                return;
            }
            Mekanism.logger.info("VoiceServer: Bound voice connection to {}.", uuid);
        } catch (Exception e) {
            Mekanism.logger.error("VoiceServer: Error while starting server-based connection.", e);
            open = false;
        }

        // Main client listen thread
        new Thread(() -> {
            while (open) {
                try {
                    short byteCount = VoiceConnection.this.input.readShort();
                    byte[] audioData = new byte[byteCount];
                    VoiceConnection.this.input.readFully(audioData);
                    if (byteCount > 0) {
                        MekanismAdditions.voiceManager.sendToPlayers(byteCount, audioData, VoiceConnection.this);
                    }
                } catch (Exception e) {
                    open = false;
                }
            }
            //No longer open, kill it
            kill();
        }, "Voice Server Client Listen Thread").start();
    }

    private void kill() {
        try {
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
            if (socket != null) {
                socket.close();
            }
            MekanismAdditions.voiceManager.removeConnection(this);
        } catch (Exception e) {
            Mekanism.logger.error("VoiceServer: Error while stopping server-based connection.", e);
        }
    }

    public void sendToPlayer(short byteCount, byte[] audioData, VoiceConnection connection) {
        if (!open || output == null) {
            if (open) {
                kill();
            }
            return;
        }
        try {
            // One recipient can be written by every sender's listen thread. The length and payload stay one frame.
            synchronized (output) {
                output.writeShort(byteCount);
                output.write(audioData);
                output.flush();
            }
        } catch (Exception e) {
            Mekanism.logger.error("VoiceServer: Error while sending data to player.", e);
        }
    }

    public boolean canListen(int channel) {
        ServerPlayer player = getPlayer();
        return player.getInventory().items.stream().anyMatch(itemStack -> canListen(channel, itemStack)) || canListen(channel, player.getOffhandItem());
    }

    private boolean canListen(int channel, ItemStack itemStack) {
        if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemWalkieTalkie walkieTalkie) {
            return walkieTalkie.getOn(itemStack) && walkieTalkie.getChannel(itemStack) == channel;
        }
        return false;
    }

    public int getCurrentChannel() {
        ItemStack itemStack = getPlayer().getInventory().getSelected();
        if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemWalkieTalkie walkieTalkie && walkieTalkie.getOn(itemStack)) {
            return walkieTalkie.getChannel(itemStack);
        }
        return 0;
    }

    public ServerPlayer getPlayer() {
        return server.getPlayerList().getPlayer(uuid);
    }
}