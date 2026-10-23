package com.technovision.advancedgenetics.gametest.forge;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.technovision.advancedgenetics.forge.ForgeConfig;
import com.technovision.advancedgenetics.forge.ForgeNetwork;
import com.technovision.advancedgenetics.gametest.TestPlatform;
import com.technovision.advancedgenetics.network.KeyPressedPacket;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkDirection;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Forge Energy, item handler capabilities, ForgeCaps saves, LivingAttackEvent, the SimpleChannel,
 * Forge's player events and ForgeConfigSpec.
 */
final class ForgeTestPlatform implements TestPlatform {

    @Override
    public CompoundTag savedGenetics(CompoundTag playerSave) {
        CompoundTag caps = playerSave.getCompound("ForgeCaps");
        String key = ComponentRegistry.PLAYER_GENETICS_ID.toString();
        return caps.contains(key, Tag.TAG_COMPOUND) ? caps.getCompound(key) : null;
    }

    @Override
    public EnergyAccess energy(BlockEntity blockEntity, Direction side) {
        IEnergyStorage energy = blockEntity.getCapability(ForgeCapabilities.ENERGY, side)
                .orElseThrow(() -> new GameTestAssertException("Missing energy capability on " + side));
        return new EnergyAccess() {
            @Override public int stored() { return energy.getEnergyStored(); }
            @Override public int capacity() { return energy.getMaxEnergyStored(); }
            @Override public int insert(int amount, boolean simulate) { return energy.receiveEnergy(amount, simulate); }
        };
    }

    @Override
    public ItemAccess items(BlockEntity blockEntity, Direction side) {
        IItemHandler items = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                .orElseThrow(() -> new GameTestAssertException("Missing item handler on " + side));
        return new ItemAccess() {
            @Override public ItemStack insert(ItemStack stack) { return ItemHandlerHelper.insertItem(items, stack, false); }
            @Override public ItemStack extract(int slot, int amount, boolean simulate) { return items.extractItem(slot, amount, simulate); }
        };
    }

    @Override
    public int countDamage(LivingEntity target, Predicate<DamageSource> filter, Runnable action) {
        AtomicInteger hits = new AtomicInteger();
        Consumer<LivingAttackEvent> counter = event -> {
            if (event.getEntity() == target && filter.test(event.getSource())) hits.incrementAndGet();
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, counter);
        try {
            action.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(counter);
        }
        return hits.get();
    }

    @Override
    public Optional<SyncGeneticsPacket> decodeGeneticsSync(Packet<?> packet) {
        if (!(packet instanceof ClientboundCustomPayloadPacket payload) || !payload.getIdentifier().equals(ForgeNetwork.CHANNEL_NAME)) {
            return Optional.empty();
        }
        FriendlyByteBuf data = payload.getData();
        if (data.readUnsignedByte() != discriminator(new SyncGeneticsPacket(new CompoundTag()))) return Optional.empty();
        return Optional.of(SyncGeneticsPacket.decode(data));
    }

    /** The index SimpleChannel writes before a message of this type. */
    private static int discriminator(Object message) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ForgeNetwork.CHANNEL.encodeMessage(message, buf);
        return buf.readUnsignedByte();
    }

    @Override
    public void receiveKeyPress(ServerPlayer player, KeyPressedPacket packet) {
        Packet<?> sent = ForgeNetwork.CHANNEL.toVanillaPacket(packet, NetworkDirection.PLAY_TO_SERVER);
        player.connection.handleCustomPayload((ServerboundCustomPayloadPacket) sent);
    }

    @Override
    public void fireSyncTrigger(ServerPlayer player, SyncTrigger trigger) {
        giveClosedChannel(player.connection.connection);
        MinecraftForge.EVENT_BUS.post(switch (trigger) {
            case LOGIN -> new PlayerEvent.PlayerLoggedInEvent(player);
            case RESPAWN -> new PlayerEvent.PlayerRespawnEvent(player, false);
            case CHANGE_DIMENSION -> new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER);
        });
    }

    /**
     * Forge's own login listeners read the connection's Netty attributes. A closed channel has none, so
     * they treat the test player as a vanilla client, and the connection still queues what it is sent.
     */
    private static void giveClosedChannel(Connection connection) {
        if (connection.channel() != null) return;
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.close();
        try {
            Field field = Connection.class.getDeclaredField("channel");
            field.setAccessible(true);
            field.set(connection, channel);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot give the test connection a channel", e);
        }
    }

    @Override
    public String correctConfig(String toml) {
        // Forge's config files keep insertion order, as this map does.
        CommentedConfig config = CommentedConfig.of(LinkedHashMap::new, TomlFormat.instance());
        TomlFormat.instance().createParser().parse(toml, config, ParsingMode.REPLACE);
        ForgeConfig.spec().correct(config);
        return TomlFormat.instance().createWriter().writeToString(config);
    }
}
