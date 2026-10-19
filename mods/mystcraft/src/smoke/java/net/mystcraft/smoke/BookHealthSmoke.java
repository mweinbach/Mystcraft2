package net.mystcraft.smoke;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import net.mystcraft.minecraft.item.BookHealth;

/** Real entity damage, saved decay boundary and classic recovery; no world fixture changes. */
public final class BookHealthSmoke {
    private BookHealthSmoke() {}
    public static List<String> run(MinecraftServer server, ServerLevel level) {
        ItemStack stack = new ItemStack(MystcraftItems.LINKING_BOOK.get());
        LinkBookEntity book = new LinkBookEntity(level, 32.5, 220, 32.5, stack);
        LinkBookEntity restored = null;
        try {
            check(BookHealth.health(book.getBook()) == 10, "default book must have ten health");
            check(!((Entity) book).hurt(DamageSource.IN_WALL, 100), "book must ignore suffocation");
            check(((Entity) book).hurt(DamageSource.GENERIC, 2) && BookHealth.health(book.getBook()) == 8,
                    "first hit must damage persistent book health");
            check(!((Entity) book).hurt(DamageSource.GENERIC, 2) && BookHealth.health(book.getBook()) == 8,
                    "equal hit inside living hurt window must not damage again");
            check(((Entity) book).hurt(DamageSource.GENERIC, 3) && BookHealth.health(book.getBook()) == 7,
                    "stronger hit in hurt window must apply only difference");
            Item item = stack.getItem();
            check(item.isBarVisible(book.getBook()) && item.getBarWidth(book.getBook()) == 9,
                    "damaged book item must expose proportional durability bar");
            CompoundTag saved = ((Entity) book).saveWithoutId(new CompoundTag());
            saved.putInt("DecayTimer", 9999);
            restored = new LinkBookEntity(level, 32.5, 220, 32.5, stack.copy());
            ((Entity) restored).load(saved);
            check(BookHealth.health(restored.getBook()) == 7, "book stack health must survive entity NBT");
            ((Entity) restored).tick();
            check(BookHealth.health(restored.getBook()) == 6, "persisted ten-thousandth tick must cause one decay damage");
            ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "SmokeBookHealth"), null);
            player.setPos(((Entity) restored).getX(), ((Entity) restored).getY(), ((Entity) restored).getZ());
            ((ItemEntity) restored).playerTouch(player);
            check(player.getInventory().isEmpty(), "walking over book must not recover it");
            player.setShiftKeyDown(true);
            ((Entity) restored).interact(player, InteractionHand.MAIN_HAND);
            check(((Entity) restored).isRemoved() && BookHealth.health(player.getItemInHand(InteractionHand.MAIN_HAND)) == 6,
                    "sneak recovery must preserve damaged stack and remove source entity");
            LinkBookEntity burning = new LinkBookEntity(level, 32.5, 220, 32.5, stack.copy());
            try {
                check(((Entity) burning).hurt(DamageSource.IN_FIRE, 2) && BookHealth.health(burning.getBook()) == 6,
                        "fire must cause double damage");
                check(((Entity) burning).hurt(DamageSource.GENERIC, 100) && !((Entity) burning).isAlive()
                                && !((Entity) burning).isRemoved(), "lethal book must enter living death delay");
                for (int tick = 0; tick < 20; tick++) ((Entity) burning).tick();
                check(((Entity) burning).isRemoved(), "book corpse must disappear after twenty loaded death ticks");
            } finally { ((Entity) burning).discard(); }
            net.minecraft.core.BlockPos wetPosition = new net.minecraft.core.BlockPos(32, 220, 32);
            check(level.getBlockEntity(wetPosition) == null, "wet book fixture cannot replace block entity");
            net.minecraft.world.level.block.state.BlockState oldWater = level.getBlockState(wetPosition);
            LinkBookEntity wet = new LinkBookEntity(level, 32.5, 220.25, 32.5, stack.copy());
            try {
                level.setBlock(wetPosition, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
                ((Entity) wet).setNoGravity(true);
                ((Entity) wet).tick();
                check(BookHealth.health(wet.getBook()) == 9, "actual water contact must cause one damage");
                ((Entity) wet).tick();
                check(BookHealth.health(wet.getBook()) == 9, "continuous wet contact must honor living hurt window");
                for (int tick = 0; tick < 9; tick++) {
                    ((Entity) wet).setPos(32.5, 220.25, 32.5);
                    ((Entity) wet).setDeltaMovement(0, 0, 0);
                    ((Entity) wet).tick();
                }
                check(BookHealth.health(wet.getBook()) == 8, "wet damage must resume when living hurt window expires");
            } finally {
                ((Entity) wet).discard();
                level.setBlock(wetPosition, oldWater, 2);
            }
            physicalLifecycle(level);
            packetAttack(server, level);
            return List.of("book health: ten-health NBT durability, suffocation immunity, living hurt window and stronger-hit difference",
                    "book health: saved decay boundary, damaged-stack sneak recovery, no auto pickup, doubled fire and lethal destruction",
                    "book health: actual water damage respects living repeated-hit timing",
                    "book lifecycle: living gravity, submerged air/save/drowning, live inventory wrapper and hopper-cart exact-NBT transfer",
                    "book health: real server attack packet damages physical book without disconnect; distant and spectator packets reject");
        } finally {
            ((Entity) book).discard();
            if (restored != null) ((Entity) restored).discard();
        }
    }
    private static void physicalLifecycle(ServerLevel level) {
        net.minecraft.core.BlockPos position = new net.minecraft.core.BlockPos(32, 220, 32);
        check(level.getBlockEntity(position) == null, "physical book fixture cannot replace block entity");
        var old = level.getBlockState(position);
        ItemStack stack = new ItemStack(MystcraftItems.LINKING_BOOK.get());
        stack.getOrCreateTag().putString("LifecycleMarker", "exact payload");
        LinkBookEntity book = new LinkBookEntity(level, 32.5, 220.25, 32.5, stack.copy());
        net.minecraft.world.entity.vehicle.MinecartHopper cart =
                new net.minecraft.world.entity.vehicle.MinecartHopper(level, 32.5, 220.25, 32.5);
        try {
            level.setBlock(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            ((Entity) book).tick();
            check(Math.abs(((Entity) book).getDeltaMovement().y + .0784) < .00001,
                    "dry book must use living .08 gravity and .98 vertical drag");
            level.setBlock(position, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
            ItemStack robust = stack.copy(); robust.getOrCreateTag().putFloat("MaxHealth", 100);
            BookHealth.set(robust, 100);
            // The dry physics probe can take real rain damage. Replacing only its stack
            // would retain that hit's living invulnerability window and alter drowning damage.
            ((Entity) book).discard();
            book = new LinkBookEntity(level, 32.5, 220.25, 32.5, robust);
            ((Entity) book).setDeltaMovement(0, 0, 0);
            ((Entity) book).setAirSupply(-18); ((Entity) book).tick();
            check(((Entity) book).getAirSupply() == 0 && BookHealth.health(book.getBook()) == 98,
                    "submerged book must consume two air and apply living two-damage drowning at minus twenty: air="
                            + ((Entity) book).getAirSupply() + ", health=" + BookHealth.health(book.getBook())
                            + ", max=" + BookHealth.maximum(book.getBook()) + ", eyeWater="
                            + ((Entity) book).isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                            + ", bodyWater=" + ((Entity) book).isInWater() + ", eyeY=" + ((Entity) book).getEyeY()
                            + ", pos=" + ((Entity) book).position() + ", fluid=" + level.getFluidState(position));
            check(((Entity) book).getDeltaMovement().y < 0,
                    "living book must sink rather than use dropped-item buoyancy");
            ((Entity) book).setAirSupply(123);
            CompoundTag saved = ((Entity) book).saveWithoutId(new CompoundTag());
            check(saved.getShort("Air") == 123, "native entity air must persist in saved NBT");
            level.setBlock(position, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), 2);
            ItemStack lavaStack = stack.copy(); lavaStack.getOrCreateTag().putFloat("MaxHealth", 1000);
            BookHealth.set(lavaStack, 1000);
            LinkBookEntity lavaBook = new LinkBookEntity(level, 32.5, 220.25, 32.5, lavaStack);
            try {
                ((Entity) lavaBook).setNoGravity(true);
                // Entity.isInLava deliberately ignores the native first tick.
                ((Entity) lavaBook).tick(); ((Entity) lavaBook).tick();
                float afterContact = BookHealth.health(lavaBook.getBook());
                check(afterContact == 992, "native lava contact must apply four living damage doubled to eight");
                ((Entity) lavaBook).tick();
                check(BookHealth.health(lavaBook.getBook()) == afterContact,
                        "continuous lava contact must respect living repeated-hit protection");
            } finally { ((Entity) lavaBook).discard(); }
            level.setBlock(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
            ((Entity) book).setPos(32.5, 220.25, 32.5); ((Entity) book).setNoGravity(true);
            ((Entity) book).setDeltaMovement(0, 0, 0);
            var wrapper = book.createBookWrapper();
            check(wrapper.getContainerSize() == 1 && wrapper.getItem(0) == book.getBook()
                    && !wrapper.canPlaceItem(0, new ItemStack(net.minecraft.world.item.Items.STONE)),
                    "book inventory wrapper must expose actual source and reject unrelated items");
            var stationary = new net.minecraft.world.SimpleContainer(1);
            check(!net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(stationary, (ItemEntity) book)
                    && stationary.isEmpty() && !((Entity) book).isRemoved(),
                    "native loose-item hopper pickup must ignore living book adapter");
            for (int slot = 0; slot < cart.getContainerSize(); slot++)
                cart.setItem(slot, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
            check(level.addFreshEntity(cart), "hopper cart must enter actual loaded entity lookup");
            ((Entity) book).tick();
            check(!((Entity) book).isRemoved(), "full hopper cart must preserve physical book");
            cart.clearContent(); ItemStack expected = book.getBook().copy();
            ((Entity) book).setPos(32.5, 220.25, 32.5); ((Entity) book).setDeltaMovement(0, 0, 0);
            ((Entity) book).tick();
            check(((Entity) book).isRemoved() && ItemStack.matches(cart.getItem(0), expected),
                    "colliding hopper cart must receive exact damaged NBT once and remove source book");
        } finally {
            ((Entity) book).discard(); cart.clearContent(); ((Entity) cart).discard();
            level.setBlock(position, old, 2);
        }
    }

    private static void packetAttack(MinecraftServer server, ServerLevel level) {
        java.util.List<net.minecraft.network.protocol.Packet<?>> sent = new java.util.ArrayList<>();
        ServerPlayer attacker = new ServerPlayer(server, level,
                new GameProfile(UUID.randomUUID(), "SmokeBookAttack"), null);
        net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { sent.add(packet); }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { sent.add(packet); }
        };
        attacker.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, attacker);
        attacker.setPos(32.5, 220, 32.5);
        attacker.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        LinkBookEntity target = new LinkBookEntity(level, 33.5, 220, 32.5,
                new ItemStack(MystcraftItems.LINKING_BOOK.get()));
        try {
            level.addNewPlayer(attacker);
            check(level.addFreshEntity(target), "attack packet book must enter actual server entity lookup");
            var packet = net.minecraft.network.protocol.game.ServerboundInteractPacket.createAttackPacket((Entity) target, false);
            attacker.setPos(100, 220, 100);
            attacker.connection.handleInteract(packet);
            check(BookHealth.health(target.getBook()) == 10, "out-of-reach attack packet must not damage book");
            attacker.setPos(32.5, 220, 32.5);
            attacker.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            attacker.connection.handleInteract(packet);
            check(BookHealth.health(target.getBook()) == 10, "spectator attack packet must not damage book");
            attacker.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            attacker.connection.handleInteract(packet);
            check(BookHealth.health(target.getBook()) < 10, "real in-range survival attack packet must damage book");
            check(sent.stream().noneMatch(p -> p instanceof net.minecraft.network.protocol.game.ClientboundDisconnectPacket),
                    "physical book attack packet must never disconnect player as invalid item attack");
        } finally {
            ((Entity) target).discard();
            level.removePlayerImmediately(attacker, Entity.RemovalReason.DISCARDED);
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
