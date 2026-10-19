package net.mystcraft.integration.network;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.mystcraft.minecraft.binder.BookBinderMenu;
import net.mystcraft.minecraft.ink.InkMixerMenu;
import net.mystcraft.minecraft.modifier.LinkModifierMenu;
import net.mystcraft.minecraft.writing.PageCollectionMenu;
import net.mystcraft.minecraft.writing.WritingDeskMenu;

/** Full-width menu actions. Vanilla's button packet truncates IDs to a signed byte. */
public final class MenuActions {
    private static Consumer<Action> sender;
    private MenuActions() {}
    public record Action(int containerId, int command) {
        public Action {
            if (containerId < 0 || containerId > 100 || command < 0 || command > 0x4ffff)
                throw new IllegalArgumentException("Invalid Mystcraft menu action");
        }
        public static Action decode(FriendlyByteBuf buffer) {
            return new Action(buffer.readVarInt(), buffer.readVarInt());
        }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId); buffer.writeVarInt(command);
        }
    }
    public static void initialize(Consumer<Action> transport) { sender = Objects.requireNonNull(transport); }
    public static void send(int containerId, int command) {
        if (sender == null) throw new IllegalStateException("Mystcraft menu transport is not initialized");
        sender.accept(new Action(containerId, command));
    }
    public static boolean handle(ServerPlayer player, Action action) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!player.isAlive() || player.isSpectator() || menu.containerId != action.containerId()
                || !(menu instanceof BookBinderMenu || menu instanceof InkMixerMenu
                || menu instanceof LinkModifierMenu || menu instanceof PageCollectionMenu
                || menu instanceof WritingDeskMenu || menu instanceof net.mystcraft.minecraft.exploration.BookDisplayMenu) || !menu.stillValid(player)) return false;
        boolean accepted = menu.clickMenuButton(player, action.command());
        if (accepted) menu.broadcastChanges();
        return accepted;
    }
}
