package net.mystcraft.minecraft.linking;

import java.util.ArrayList;
import java.util.List;
import net.mystcraft.minecraft.item.LinkingBookItem;
import net.mystcraft.minecraft.item.BookHealth;
import net.mystcraft.minecraft.age.AgeManager;
import net.mystcraft.integration.registry.MystcraftSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.mystcraft.minecraft.entity.LinkBookEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.mystcraft.integration.platform.GameplayPlatform;
import net.mystcraft.integration.network.AgeVisualNetwork;

/** Server-only linking. A UI confirmation must revalidate its session before calling this service. */
public final class LinkingService {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private LinkingService() {}

    public static boolean link(ServerPlayer player, InteractionHand hand, ItemStack expectedBook) {
        ItemStack held = player.getItemInHand(hand);
        if (held != expectedBook) {
            return fail(player, "invalid_book");
        }
        return link(player, held, hand, false);
    }

    /** Caller must validate the loaded stand, interaction distance and stored stack identity. */
    public static boolean linkFromStand(ServerPlayer player, ItemStack book) {
        return link(player, book, null, false);
    }

    /** Caller must validate the loaded entity, interaction distance and stored stack identity. */
    public static boolean linkFromEntity(ServerPlayer player, ItemStack book) {
        return link(player, book, null, false);
    }

    public static boolean linkFromPortal(ServerPlayer player, ItemStack book) {
        return link(player, book, null, true);
    }

    /** External portal links retain their source book and may replace the traveling nonplayer. */
    public static Entity linkFromPortal(Entity entity, ItemStack book) {
        if (entity instanceof ServerPlayer player) return linkFromPortal(player, book) ? player : null;
        if (!(entity.level instanceof ServerLevel origin) || !entity.isAlive() || entity.isPassenger()
                || entity.isVehicle() || book.isEmpty() || !(book.getItem() instanceof LinkingBookItem)
                || BookHealth.health(book) <= 0) return null;
        LinkModifiers modifiers = LinkModifiers.from(book);
        if (modifiers.disarm() && entity instanceof ItemEntity) return null;
        if (!AgeManager.prepare(entity, book)) return null;
        var parsed = LinkDestination.read(book);
        if (parsed.isEmpty()) return null;
        LinkDestination destination = parsed.get();
        ServerLevel target = AgeManager.ensureLoaded(origin.getServer(), destination.dimension());
        if (target == null || LinkingRules.dead(origin.getServer(),target.dimension()) || !LinkingRules.matchesBook(origin.getServer(),target.dimension(),book) || !modifiers.allowsDimension(target == origin)) return null;
        Vec3 departure = entity.position(), beforeMotion = entity.getDeltaMovement();
        float fallDistance = entity.fallDistance;
        if (modifiers.relative()) {
            BlockPos from = origin.getSharedSpawnPos(), to = target.getSharedSpawnPos();
            destination = new LinkDestination(destination.dimensionId(), to.getX() + (int) (departure.x - from.getX()),
                    to.getY() + (int) (departure.y - from.getY()), to.getZ() + (int) (departure.z - from.getZ()),
                    destination.yaw(), destination.pitch());
        }
        Vec3 arrival = new Vec3(destination.x(), destination.y(), destination.z());
        BlockPos feet = new BlockPos(arrival);
        AABB bounds = entity.getBoundingBox().move(arrival.subtract(departure));
        if (arrival.y < target.getMinBuildHeight() || bounds.maxY > target.getMaxBuildHeight()
                || !target.getWorldBorder().isWithinBounds(bounds)) return null;
        target.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
        if (!target.noCollision(entity, bounds) || target.containsAnyLiquid(bounds)) return null;
        // Source onPortalCollision always disables Generate Platform for external links.
        BlockPos platform = null;
        BlockState previous = platform == null ? null : target.getBlockState(platform);
        ProvisionalDrops drops = new ProvisionalDrops();
        boolean committed = false;
        try {
            if (platform != null && !target.setBlock(platform, Blocks.STONE.defaultBlockState(), 3)) return null;
            if (modifiers.disarm() && !dropNonplayerInventory(entity, drops)) return null;
            Entity moved = GameplayPlatform.transfer(entity, target, arrival, destination.yaw(), destination.pitch());
            if (moved == null) return null;
            if (modifiers.disarm()) clearNonplayerInventory(moved);
            moved.setDeltaMovement(modifiers.arrivalMotion(beforeMotion, destination.yaw(), true));
            moved.fallDistance = fallDistance;
            moved.setPortalCooldown();
            drops.release();
            committed = true;
            ClassicLinkEffects.emit(origin, departure, entity, modifiers, true);
            ClassicLinkEffects.emit(target, arrival, moved, modifiers, true);
            return moved;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not access vanilla horse/mob inventory for Disarm", failure);
        } finally {
            if (!committed) {
                for (Entity drop : drops) drop.discard();
                if (platform != null) target.setBlock(platform, previous, 3);
            }
        }
    }

    private static boolean link(ServerPlayer player, ItemStack held, InteractionHand hand, boolean forceMaintain) {
        if (held.isEmpty() || !(held.getItem() instanceof LinkingBookItem) || BookHealth.health(held) <= 0) return fail(player, "invalid_book");
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping()
                || player.isChangingDimension()) return fail(player, "cannot_link");
        if (!LinkPermissions.get(player.server).allows(player.getGameProfile().getName(),LinkPermissions.Direction.DEPART,player.level.dimension().location()))
            return fail(player,"permission_denied");
        if (!AgeManager.prepare(player, held)) return false;
        var parsed = LinkDestination.read(held);
        if (parsed.isEmpty()) return fail(player, "invalid_book");
        LinkDestination destination = parsed.get();
        LinkModifiers modifiers = LinkModifiers.from(held);
        ServerLevel origin = player.getLevel();
        Vec3 departure = player.position();
        Vec3 beforeMotion = player.getDeltaMovement();
        float beforeFallDistance = player.fallDistance;
        ServerLevel target = AgeManager.ensureLoaded(player.server, destination.dimension());
        if (target == null) return fail(player, "missing_dimension");
        if (!LinkingRules.matchesBook(player.server,target.dimension(),held)) return fail(player,"wrong_dimension");
        if (!LinkingRules.allows(player,target.dimension(),false)) return fail(player,"permission_denied");
        if (!modifiers.allowsDimension(target == origin)) return fail(player, "intra_link_disabled");

        if (modifiers.relative()) {
            BlockPos sourceSpawn = origin.getSharedSpawnPos(), targetSpawn = target.getSharedSpawnPos();
            destination = new LinkDestination(destination.dimensionId(),
                    targetSpawn.getX() + (int) (departure.x - sourceSpawn.getX()),
                    targetSpawn.getY() + (int) (departure.y - sourceSpawn.getY()),
                    targetSpawn.getZ() + (int) (departure.z - sourceSpawn.getZ()),
                    destination.yaw(), destination.pitch());
        }

        BlockPos pos = new BlockPos(destination.x(), destination.y(), destination.z());
        AABB bounds = player.getBoundingBox().move(destination.x() - player.getX(),
                destination.y() - player.getY(), destination.z() - player.getZ());
        if (destination.y() < target.getMinBuildHeight() || bounds.maxY > target.getMaxBuildHeight()
                || !target.getWorldBorder().isWithinBounds(bounds)) return fail(player, "unsafe_destination");
        // Load the arrival chunk before collision checks; never guess a replacement destination.
        target.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (!target.noCollision(player, bounds) || target.containsAnyLiquid(bounds)) {
            return fail(player, "unsafe_destination");
        }

        // Spawn provisional drops while inventory is intact. Vetoes must leave neither lost
        // inventory nor duplicates, including custom book entities created by loader drop hooks.
        ProvisionalDrops drops = new ProvisionalDrops();
        BlockPos platform = !forceMaintain && modifiers.generatePlatform() && target.isEmptyBlock(pos.below())
                && target.isEmptyBlock(pos.below(2)) ? pos.below() : null;
        BlockState previousPlatform = platform == null ? null : target.getBlockState(platform);
        boolean committed = false;
        try {
            if (platform != null && !target.setBlock(platform, Blocks.STONE.defaultBlockState(), 3)) {
                return fail(player, "unsafe_destination");
            }
            boolean leaveHeld = hand != null && !modifiers.following();
            if (leaveHeld) {
                LinkBookEntity sourceBook = new LinkBookEntity(origin, departure.x, departure.y + 0.3,
                        departure.z, held.copy());
                sourceBook.setDefaultPickUpDelay();
                if (!spawn(origin, sourceBook, drops)) return fail(player, "cannot_link");
            }
            if (modifiers.disarm() && !dropInventory(player, hand, leaveHeld, drops)) {
                return fail(player, "cannot_link");
            }
            AgeVisualNetwork.syncLevel(player, target);
            player.teleportTo(target, destination.x(), destination.y(), destination.z(),
                    destination.yaw(), destination.pitch());
            if (player.getLevel() != target || player.position().distanceToSqr(
                    new Vec3(destination.x(), destination.y(), destination.z())) > 1.0E-8) {
                LOGGER.warn("Linking player travel rejected: source={}, target={}, actual={}, position={}",
                        origin.dimension().location(), target.dimension().location(), player.getLevel().dimension().location(), player.position());
                return fail(player, "cannot_link");
            }
            if (modifiers.disarm()) player.getInventory().clearContent();
            else if (leaveHeld) player.setItemInHand(hand, ItemStack.EMPTY);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            drops.release();
            committed = true;
        } finally {
            if (!committed) {
                for (Entity drop : drops) drop.discard();
                if (platform != null) target.setBlock(platform, previousPlatform, 3);
            }
        }
        ClassicLinkEffects.emit(origin, departure, player, modifiers, forceMaintain);
        ClassicLinkEffects.emit(target, player.position(), player, modifiers, forceMaintain);
        player.setDeltaMovement(modifiers.arrivalMotion(beforeMotion, destination.yaw(), forceMaintain));
        player.fallDistance = modifiers.maintainMomentum() || forceMaintain ? beforeFallDistance : 0;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
        return true;
    }

    private static boolean spawn(ServerLevel level, Entity entity, ProvisionalDrops provisional) {
        // Track even canceled entities: integrations may have queued them before vetoing insertion.
        provisional.add(entity);
        if (entity instanceof ItemEntity item) {
            // Vanilla's never-pickup sentinel also disables merging. Until commit, every
            // stack must stay in an owned entity so veto rollback cannot duplicate or lose it.
            provisional.pickupDelays.put(item, (int) item.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getShort("PickupDelay"));
            item.setNeverPickUp();
        }
        boolean added = level.addFreshEntity(entity);
        if (!added) LOGGER.warn("Linking provisional drop insertion rejected: dimension={}, type={}, uuid={}, removed={}, item={}, count={}, provisional={}",
                level.dimension().location(), net.minecraft.core.Registry.ENTITY_TYPE.getKey(entity.getType()),
                entity.getUUID(), entity.isRemoved(), entity instanceof ItemEntity item
                        ? net.minecraft.core.Registry.ITEM.getKey(item.getItem().getItem()) : null,
                entity instanceof ItemEntity item ? item.getItem().getCount() : 0, provisional.size());
        return added;
    }

    private static boolean dropInventory(ServerPlayer player, InteractionHand hand, boolean sourceBookDropped,
                                         ProvisionalDrops provisional) {
        Inventory inventory = player.getInventory();
        int sourceSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected
                : inventory.items.size() + inventory.armor.size();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (sourceBookDropped && slot == sourceSlot) continue;
            if (!dropStack(player, inventory.getItem(slot), provisional)) return false;
        }
        return true;
    }

    private static boolean dropStack(Entity source, ItemStack stack, ProvisionalDrops provisional) {
        ServerLevel origin = (ServerLevel) source.level;
        ItemStack remaining = stack.copy();
        double x = source.getX() + origin.random.nextFloat() * .8F + .1F;
        double y = source.getY() + origin.random.nextFloat() * .8F + .1F;
        double z = source.getZ() + origin.random.nextFloat() * .8F + .1F;
        while (!remaining.isEmpty()) {
            ItemStack split = remaining.split(origin.random.nextInt(21) + 10);
            Entity drop = split.getItem() instanceof LinkingBookItem ? new LinkBookEntity(origin, x, y, z, split)
                    : new ItemEntity(origin, x, y, z, split);
            drop.setDeltaMovement(origin.random.nextGaussian() * .05, origin.random.nextGaussian() * .05 + .2,
                    origin.random.nextGaussian() * .05);
            if (!spawn(origin, drop, provisional)) return false;
        }
        return true;
    }

    private static List<Container> containers(Entity entity) throws ReflectiveOperationException {
        List<Container> result = new ArrayList<>();
        if (entity instanceof Container container) result.add(container);
        if (entity instanceof AbstractHorse) {
            for (java.lang.reflect.Field field : AbstractHorse.class.getDeclaredFields()) {
                if (Container.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Container horse = (Container) field.get(entity);
                    if (horse != null && !result.contains(horse)) result.add(horse);
                }
            }
        }
        return result;
    }

    private static boolean dropNonplayerInventory(Entity entity, ProvisionalDrops provisional) throws ReflectiveOperationException {
        List<ItemStack> containerItems = new ArrayList<>();
        for (Container container : containers(entity)) for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot); containerItems.add(stack);
            if (!dropStack(entity, stack, provisional)) return false;
        }
        if (entity instanceof Mob mob) for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            // Horse armor may be the same stack already owned by the horse inventory.
            boolean ownedByContainer = containerItems.stream().anyMatch(item -> item == stack);
            if (!stack.isEmpty() && !ownedByContainer && equipmentDropChance(mob, slot) > 1
                    && !dropStack(entity, stack, provisional)) return false;
        }
        return true;
    }

    private static float equipmentDropChance(Mob mob, EquipmentSlot slot) throws ReflectiveOperationException {
        int length = slot.getType() == EquipmentSlot.Type.HAND ? 2 : 4;
        for (java.lang.reflect.Field field : Mob.class.getDeclaredFields()) if (field.getType() == float[].class) {
            field.setAccessible(true); float[] chances = (float[]) field.get(mob);
            if (chances.length == length) return chances[slot.getIndex()];
        }
        throw new IllegalStateException("Missing vanilla mob equipment drop chances");
    }

    private static void clearNonplayerInventory(Entity entity) throws ReflectiveOperationException {
        for (Container container : containers(entity)) { container.clearContent(); container.setChanged(); }
        if (entity instanceof Mob mob) for (EquipmentSlot slot : EquipmentSlot.values()) mob.setItemSlot(slot, ItemStack.EMPTY);
    }

    private static final class ProvisionalDrops extends ArrayList<Entity> {
        private final java.util.IdentityHashMap<ItemEntity, Integer> pickupDelays = new java.util.IdentityHashMap<>();
        void release() {
            pickupDelays.forEach(ItemEntity::setPickUpDelay);
        }
    }

    private static boolean fail(ServerPlayer player, String reason) {
        player.displayClientMessage(Component.translatable("message.mystcraft." + reason), true);
        return false;
    }
}
