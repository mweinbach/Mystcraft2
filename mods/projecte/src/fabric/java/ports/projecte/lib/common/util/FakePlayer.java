/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A server player with no client, used to act in the world on a machine's behalf. It is never added to the player
 * list, ignores damage and messages, and drops every packet sent to it.
 */
public class FakePlayer extends ServerPlayer {

    public FakePlayer(ServerLevel level, GameProfile name) {
        super(level.getServer(), level, name, null);
        this.connection = new FakePlayerNetHandler(level.getServer(), this);
    }

    @Override
    public void displayClientMessage(Component chatComponent, boolean actionBar) {
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean canHarmPlayer(Player player) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
    }

    @Override
    public void tick() {
    }

    @Override
    public void updateOptions(ServerboundClientInformationPacket packet) {
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return level.getServer();
    }

    @Nullable
    @Override
    public Entity changeDimension(ServerLevel destination) {
        return null;
    }

    private static class FakePlayerNetHandler extends ServerGamePacketListenerImpl {

        FakePlayerNetHandler(MinecraftServer server, ServerPlayer player) {
            super(server, new Connection(PacketFlow.CLIENTBOUND), player);
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        }
    }
}
