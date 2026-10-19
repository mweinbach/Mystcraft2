package net.mystcraft.minecraft.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.mystcraft.minecraft.item.BookHealth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.mystcraft.integration.registry.BookEntityRegistry;
import net.mystcraft.minecraft.item.LinkingBookItem;
import net.mystcraft.integration.platform.GameplayPlatform;

/** Persistent in-world book: click to read, sneak with an empty hand to recover. */
public final class LinkBookEntity extends ItemEntity {
    private static final byte BOOK_HURT_EVENT = 61;
    private int visualHurtTicks;
    private int decayTimer;
    private int deathTicks;
    private int bookInvulnerableTime;
    private float lastBookDamage;
    public LinkBookEntity(EntityType<? extends LinkBookEntity> type, Level level) {
        super(type, level);
        setUnlimitedLifetime();
    }

    public LinkBookEntity(Level level, double x, double y, double z, ItemStack book) {
        this(BookEntityRegistry.LINK_BOOK.get(), level);
        setPos(x, y, z);
        setItem(book);
        setDeltaMovement(0, 0, 0);
    }

    public ItemStack getBook() { return getItem(); }
    public boolean isVisuallyHurt() { return visualHurtTicks > 0; }

    @Override public void handleEntityEvent(byte event) {
        if (event == BOOK_HURT_EVENT) visualHurtTicks = 10;
        else super.handleEntityEvent(event);
    }

    @Override
    public boolean isAttackable() { return !isRemoved(); }

    @Override
    public boolean isPickable() { return !isRemoved(); }

    @Override
    public void playerTouch(Player player) {
        // Books are deliberately recovered through interaction, never by walking over them.
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSpectator() || !isAlive() || player.distanceToSqr(this) > 64) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack book = getBook();
            if (!(book.getItem() instanceof LinkingBookItem)) return InteractionResult.PASS;
            if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
                player.setItemInHand(hand, book.copy());
                setItem(ItemStack.EMPTY);
                discard();
                player.getInventory().setChanged();
            } else {
                net.mystcraft.integration.network.BookDisplayNetwork.open(serverPlayer, this);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void tick() {
        if (visualHurtTicks > 0) --visualHurtTicks;
        if (bookInvulnerableTime > 0) --bookInvulnerableTime;
        // ItemEntity.tick adds buoyancy, .04 gravity, lava bouncing and item merging.
        // The classic entity was living: retain Entity's environmental/fire handling,
        // then use its passive living movement rather than those dropped-item behaviors.
        xo = getX(); yo = getY(); zo = getZ();
        baseTick();
        if (isRemoved()) return;
        if (getBook().isEmpty()) { discard(); return; }
        boolean dying = BookHealth.health(getBook()) <= 0;
        boolean water = isInWater(), lava = isInLava();
        move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        float friction = water ? .8F : lava ? .5F : onGround
                ? level.getBlockState(getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * .91F : .91F;
        if (!water && !lava && !isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -.08, 0));
        setDeltaMovement(getDeltaMovement().multiply(friction, water ? .8 : lava ? .5 : .98, friction));
        if ((water || lava) && !isNoGravity()) setDeltaMovement(getDeltaMovement().add(0, -.02, 0));
        if (dying) {
            if (++deathTicks >= 20) discard();
            return;
        }
        if (!level.isClientSide) {
            if (!(getBook().getItem() instanceof LinkingBookItem)) { discard(); return; }
            if (isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
                setAirSupply(getAirSupply() - 2);
                if (getAirSupply() <= -20) {
                    setAirSupply(0);
                    hurt(DamageSource.DROWN, 2);
                    ((net.minecraft.server.level.ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                            getX(), getY(), getZ(), 8, .1, .1, .1, 0);
                }
            } else setAirSupply(Math.min(getAirSupply() + 4, getMaxAirSupply()));
            if (++decayTimer >= 10000) { decayTimer = 0; hurt(DamageSource.STARVE, 1); }
            if (isInWaterOrRain()) hurt(DamageSource.DROWN, 1);
            collectByHopperMinecart();
        }
    }

    private void collectByHopperMinecart() {
        for (var cart : level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.MinecartHopper.class, getBoundingBox())) {
            ItemStack before = getBook();
            ItemStack remainder = net.minecraft.world.level.block.entity.HopperBlockEntity.addItem(null, cart, before.copy(), null);
            if (remainder.getCount() != before.getCount()) {
                setItem(remainder);
                if (remainder.isEmpty()) discard();
                return;
            }
        }
    }

    /** Source GUI wrapper, deliberately not a world Container (stationary hoppers must not collect it). */
    public net.minecraft.world.Container createBookWrapper() {
        return new net.minecraft.world.SimpleContainer(1) {
            @Override public ItemStack getItem(int slot) { return slot == 0 ? getBook() : ItemStack.EMPTY; }
            @Override public int getContainerSize() { return 1; }
            @Override public boolean isEmpty() { return getBook().isEmpty(); }
            @Override public boolean canPlaceItem(int slot, ItemStack stack) {
                return slot == 0 && stack.getItem() instanceof LinkingBookItem;
            }
            @Override public void setItem(int slot, ItemStack stack) {
                if (slot == 0 && (stack.isEmpty() || canPlaceItem(slot, stack))) {
                    LinkBookEntity.this.setItem(stack);
                    if (stack.isEmpty()) discard();
                }
            }
            @Override public ItemStack removeItem(int slot, int amount) {
                if (slot != 0 || amount <= 0) return ItemStack.EMPTY;
                ItemStack remaining = getBook().copy();
                ItemStack result = remaining.split(amount);
                LinkBookEntity.this.setItem(remaining);
                if (remaining.isEmpty()) discard();
                return result;
            }
            @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }
            @Override public void clearContent() { setItem(0, ItemStack.EMPTY); }
            @Override public boolean stillValid(Player player) {
                return LinkBookEntity.this.isAlive() && player.level == level
                        && (level.isClientSide || ((net.minecraft.server.level.ServerLevel) level).getEntity(getUUID()) == LinkBookEntity.this)
                        && player.distanceToSqr(LinkBookEntity.this) <= 64;
            }
        };
    }

    @Override public boolean isPushable() { return isAlive(); }

    @Override public boolean isAlive() { return !isRemoved() && !getBook().isEmpty() && BookHealth.health(getBook()) > 0; }

    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        int damage = (int) Math.ceil((distance - 3) * multiplier);
        return damage > 0 && hurt(source, damage);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level.isClientSide || isRemoved() || source == DamageSource.IN_WALL || isInvulnerableTo(source)
                || !Float.isFinite(amount) || amount <= 0 || getBook().isEmpty() || BookHealth.health(getBook()) <= 0) return false;
        if (source.isFire()) { amount = Math.min(Float.MAX_VALUE / 2, amount) * 2; setSecondsOnFire((int) Math.min(amount, 300)); }
        float applied = amount;
        // LivingEntity's classic 20-tick hurt window: stronger hits apply only the difference.
        if (bookInvulnerableTime > 10) {
            if (amount <= lastBookDamage) return false;
            applied -= lastBookDamage;
        } else bookInvulnerableTime = 20;
        lastBookDamage = amount;
        ItemStack book = getBook().copy();
        BookHealth.set(book, BookHealth.health(book) - applied);
        setItem(book);
        level.broadcastEntityEvent(this, BOOK_HURT_EVENT);
        if (BookHealth.health(book) <= 0) deathTicks = 0;
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("DecayTimer", decayTimer);
        tag.putInt("BookDeathTime", deathTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        decayTimer = Math.max(0, tag.getInt("DecayTimer")) % 10000;
        deathTicks = Math.max(0, Math.min(19, tag.getInt("BookDeathTime")));
    }

    @Override
    public Packet<?> getAddEntityPacket() { return GameplayPlatform.spawnPacket(this); }
}
