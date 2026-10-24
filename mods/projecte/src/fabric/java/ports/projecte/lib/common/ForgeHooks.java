/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.event.entity.living.LivingAttackEvent;
import ports.projecte.lib.event.entity.living.LivingDamageEvent;
import ports.projecte.lib.event.entity.living.LivingDeathEvent;
import ports.projecte.lib.event.entity.living.LivingEvent;
import ports.projecte.lib.event.entity.living.LivingFallEvent;
import ports.projecte.lib.event.entity.living.LivingHurtEvent;
import ports.projecte.lib.event.entity.living.ShieldBlockEvent;
import ports.projecte.lib.event.level.BlockEvent;

public final class ForgeHooks {

    private static final ThreadLocal<Player> CRAFTING_PLAYER = new ThreadLocal<>();
    /** Experience from the break event vanilla is in the middle of, dropped after the block is removed. */
    private static final ThreadLocal<Integer> BREAK_EXP = new ThreadLocal<>();
    /** Position {@code getDestroyProgress} is measuring, read by the break-speed event and then cleared. */
    private static final ThreadLocal<BlockPos> BREAK_POS = new ThreadLocal<>();

    private ForgeHooks() {
    }

    public static void setCraftingPlayer(@Nullable Player player) {
        CRAFTING_PLAYER.set(player);
    }

    @Nullable
    public static Player getCraftingPlayer() {
        return CRAFTING_PLAYER.get();
    }

    /**
     * The burn time of a fuel in ticks. An item that overrides {@code getBurnTime} wins when it returns zero or more;
     * {@code -1} means Fabric's fuel registry, which includes the vanilla fuels.
     */
    public static int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        if (stack.isEmpty()) {
            return 0;
        }
        int burnTime = stack.getItem().getBurnTime(stack, recipeType);
        if (burnTime >= 0) {
            return burnTime;
        }
        Integer registered = FuelRegistry.INSTANCE.get(stack.getItem());
        return registered == null ? 0 : registered;
    }

    /**
     * Experience a break would drop, from the block's {@code getExpDrop} ({@code projecte$getExpDrop} here), or zero when the tool cannot harvest it.
     */
    public static int getExpDrop(Level level, BlockPos pos, BlockState state, Player player) {
        if (!player.hasCorrectToolForDrops(state)) {
            return 0;
        }
        ItemStack tool = player.getMainHandItem();
        int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
        int silk = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, tool);
        return state.projecte$getExpDrop(level, level.random, pos, fortune, silk);
    }

    public static void captureBreakExp(int exp) {
        BREAK_EXP.set(exp);
    }

    public static int consumeBreakExp() {
        Integer exp = BREAK_EXP.get();
        BREAK_EXP.remove();
        return exp == null ? 0 : exp;
    }

    public static ShieldBlockEvent onShieldBlock(LivingEntity blocker, DamageSource source, float blocked) {
        ShieldBlockEvent event = new ShieldBlockEvent(blocker, source, blocked);
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    public static float onLivingHurt(LivingEntity entity, DamageSource source, float amount) {
        LivingHurtEvent event = new LivingHurtEvent(entity, source, amount);
        return MinecraftForge.EVENT_BUS.post(event) ? 0 : event.getAmount();
    }

    /**
     * @return {@code true} when the entity should skip the rest of its tick
     */
    public static boolean onLivingTick(LivingEntity entity) {
        return MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(entity));
    }

    /**
     * Players post the attack from {@link #onPlayerAttack}; this returns immediately for them so a player that reached
     * {@code LivingEntity.hurt} does not post twice.
     *
     * @return {@code true} when the hurt should proceed
     */
    public static boolean onLivingAttack(LivingEntity entity, DamageSource source, float amount) {
        return entity instanceof Player || !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(entity, source, amount));
    }

    /**
     * @return {@code true} when the hurt should proceed
     */
    public static boolean onPlayerAttack(LivingEntity entity, DamageSource source, float amount) {
        return !MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(entity, source, amount));
    }

    public static float onLivingDamage(LivingEntity entity, DamageSource source, float amount) {
        LivingDamageEvent event = new LivingDamageEvent(entity, source, amount);
        return MinecraftForge.EVENT_BUS.post(event) ? 0 : event.getAmount();
    }

    /**
     * @return {@code true} when death was canceled
     */
    public static boolean onLivingDeath(LivingEntity entity, DamageSource source) {
        return MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(entity, source));
    }

    /**
     * @return the adjusted distance and multiplier, or {@code null} when the fall was canceled
     */
    @Nullable
    public static float[] onLivingFall(LivingEntity entity, float distance, float damageMultiplier) {
        LivingFallEvent event = new LivingFallEvent(entity, distance, damageMultiplier);
        return MinecraftForge.EVENT_BUS.post(event) ? null : new float[]{event.getDistance(), event.getDamageMultiplier()};
    }

    public static void onLivingJump(LivingEntity entity) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(entity));
    }

    public static void setBreakPos(@Nullable BlockPos pos) {
        BREAK_POS.set(pos);
    }

    public static void clearBreakPos() {
        BREAK_POS.remove();
    }

    @Nullable
    public static BlockPos pollBreakPos() {
        BlockPos pos = BREAK_POS.get();
        BREAK_POS.remove();
        return pos;
    }

    /**
     * Asks whether a player may break a block, the way vanilla's own checks and the break event do.
     *
     * @return the experience to drop, or -1 if the block must not be broken
     */
    public static int onBlockBreakEvent(Level level, GameType gameType, ServerPlayer player, BlockPos pos) {
        boolean preCancelEvent = false;
        ItemStack itemstack = player.getMainHandItem();
        if (!itemstack.isEmpty() && !itemstack.getItem().canAttackBlock(level.getBlockState(pos), level, pos, player)) {
            preCancelEvent = true;
        }
        if (gameType.isBlockPlacingRestricted()) {
            if (gameType == GameType.SPECTATOR) {
                preCancelEvent = true;
            }
            if (!player.mayBuild() && (itemstack.isEmpty() || !itemstack.hasAdventureModeBreakTagForBlock(level.registryAccess().registryOrThrow(Registry.BLOCK_REGISTRY),
                  new BlockInWorld(level, pos, false)))) {
                preCancelEvent = true;
            }
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) {
            preCancelEvent = true;
        }
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        if (preCancelEvent) {
            event.setCanceled(true);
        } else {
            MinecraftForge.EVENT_BUS.post(event);
        }
        if (event.isCanceled()) {
            player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
            return -1;
        }
        return event.getExpToDrop();
    }
}
