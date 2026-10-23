package com.technovision.advancedgenetics.gametest.fabric;

import com.technovision.advancedgenetics.fabric.FabricConfig;
import com.technovision.advancedgenetics.fabric.GeneticsHolder;
import com.technovision.advancedgenetics.gametest.TestPlatform;
import com.technovision.advancedgenetics.network.KeyPressedPacket;
import com.technovision.advancedgenetics.network.ModNetwork;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import team.reborn.energy.api.EnergyStorage;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Team Reborn Energy, the Fabric Transfer API, the top-level genetics save key, ALLOW_DAMAGE, the
 * play-channel packets, Fabric API's player events and FabricConfig.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricTestPlatform implements TestPlatform {

    private record DamageProbe(LivingEntity target, Predicate<DamageSource> filter, AtomicInteger hits) { }

    private static volatile DamageProbe probe;

    static void registerDamageProbe() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            DamageProbe active = probe;
            if (active != null && entity == active.target() && active.filter().test(source)) active.hits().incrementAndGet();
            return true;
        });
    }

    @Override
    public CompoundTag savedGenetics(CompoundTag playerSave) {
        return playerSave.contains(GeneticsHolder.SAVE_KEY, Tag.TAG_COMPOUND) ? playerSave.getCompound(GeneticsHolder.SAVE_KEY) : null;
    }

    @Override
    public EnergyAccess energy(BlockEntity blockEntity, Direction side) {
        EnergyStorage energy = EnergyStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (energy == null) throw new GameTestAssertException("Missing energy storage on " + side);
        return new EnergyAccess() {
            @Override public int stored() { return (int) energy.getAmount(); }
            @Override public int capacity() { return (int) energy.getCapacity(); }
            @Override
            public int insert(int amount, boolean simulate) {
                try (Transaction transaction = Transaction.openOuter()) {
                    long inserted = energy.insert(amount, transaction);
                    if (!simulate) transaction.commit();
                    return (int) inserted;
                }
            }
        };
    }

    @Override
    public ItemAccess items(BlockEntity blockEntity, Direction side) {
        Storage<ItemVariant> found = ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        if (!(found instanceof InventoryStorage items)) throw new GameTestAssertException("Missing item storage on " + side + ": " + found);
        return new ItemAccess() {
            @Override
            public ItemStack insert(ItemStack stack) {
                try (Transaction transaction = Transaction.openOuter()) {
                    long inserted = items.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                    transaction.commit();
                    ItemStack remainder = stack.copy();
                    remainder.shrink((int) inserted);
                    return remainder;
                }
            }

            @Override
            public ItemStack extract(int slot, int amount, boolean simulate) {
                SingleSlotStorage<ItemVariant> storage = items.getSlot(slot);
                ItemVariant variant = storage.getResource();
                if (variant.isBlank()) return ItemStack.EMPTY;
                try (Transaction transaction = Transaction.openOuter()) {
                    long extracted = storage.extract(variant, amount, transaction);
                    if (!simulate) transaction.commit();
                    return extracted == 0 ? ItemStack.EMPTY : variant.toStack((int) extracted);
                }
            }
        };
    }

    @Override
    public int countDamage(LivingEntity target, Predicate<DamageSource> filter, Runnable action) {
        DamageProbe active = new DamageProbe(target, filter, new AtomicInteger());
        probe = active;
        try {
            action.run();
        } finally {
            probe = null;
        }
        return active.hits().get();
    }

    @Override
    public Optional<SyncGeneticsPacket> decodeGeneticsSync(Packet<?> packet) {
        if (!(packet instanceof ClientboundCustomPayloadPacket payload) || !payload.getIdentifier().equals(ModNetwork.SYNC_GENETICS)) {
            return Optional.empty();
        }
        return Optional.of(SyncGeneticsPacket.decode(payload.getData()));
    }

    /**
     * {@code ClientPlayNetworking.send} builds this packet; the networking API routes it to the receiver.
     * The API skips payloads handled on the server thread, so it arrives from another thread, as from Netty.
     */
    @Override
    public void receiveKeyPress(ServerPlayer player, KeyPressedPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        KeyPressedPacket.encode(packet, buf);
        ServerboundCustomPayloadPacket payload = new ServerboundCustomPayloadPacket(ModNetwork.KEY_PRESSED, buf);
        CompletableFuture.runAsync(() -> player.connection.handleCustomPayload(payload)).join();
    }

    @Override
    public void fireSyncTrigger(ServerPlayer player, SyncTrigger trigger) {
        switch (trigger) {
            case LOGIN -> ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(player.connection, ServerPlayNetworking.getSender(player), player.server);
            case RESPAWN -> ServerPlayerEvents.AFTER_RESPAWN.invoker().afterRespawn(player, player, false);
            case CHANGE_DIMENSION -> ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.invoker()
                    .afterChangeWorld(player, player.server.overworld(), player.server.getLevel(Level.NETHER));
        }
    }

    @Override
    public String correctConfig(String toml) {
        return FabricConfig.correct(toml).text();
    }
}
