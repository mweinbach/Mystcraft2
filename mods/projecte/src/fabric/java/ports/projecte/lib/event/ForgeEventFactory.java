/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.common.MinecraftForge;
import ports.projecte.lib.common.util.BlockSnapshot;
import ports.projecte.lib.event.entity.living.LivingSpawnEvent;
import ports.projecte.lib.event.entity.player.PlayerEvent;
import ports.projecte.lib.event.level.BlockEvent;

/**
 * The Forge event hooks Mekanism calls directly. Fabric has no standard events for explosions starting, items
 * breaking or bows firing, so those only reach listeners on the port's own bus.
 */
public final class ForgeEventFactory {

    private ForgeEventFactory() {
    }

    /**
     * @return {@code true} if the explosion was canceled
     */
    public static boolean onExplosionStart(Level level, Explosion explosion) {
        return false;
    }

    public static void onPlayerDestroyItem(Player player, ItemStack stack, @Nullable InteractionHand hand) {
    }

    /**
     * @return {@code true} if the placement was canceled
     */
    public static boolean onBlockPlace(@Nullable Entity entity, BlockSnapshot blockSnapshot, Direction direction) {
        BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(blockSnapshot,
              blockSnapshot.getLevel().getBlockState(blockSnapshot.getPos().relative(direction.getOpposite())), entity);
        return MinecraftForge.EVENT_BUS.post(event);
    }

    public static int onArrowLoose(ItemStack stack, Level level, Player player, int charge, boolean hasAmmo) {
        return charge;
    }

    public static void onPlayerPreTick(Player player) {
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.START, player));
    }

    public static void onPlayerPostTick(Player player) {
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    /**
     * @return the speed after listeners, or {@code -1} when a listener canceled the break
     */
    public static float getBreakSpeed(Player player, BlockState state, float original, @Nullable BlockPos pos) {
        PlayerEvent.BreakSpeed event = new PlayerEvent.BreakSpeed(player, state, original, pos);
        return MinecraftForge.EVENT_BUS.post(event) ? -1 : event.getNewSpeed();
    }

    public static void firePlayerSmeltedEvent(Player player, ItemStack smelted) {
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemSmeltedEvent(player, smelted));
    }

    public static void firePlayerCraftingEvent(Player player, ItemStack crafted, Container craftMatrix) {
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, crafted, craftMatrix));
    }

    /**
     * @return whether the mob may grief, which is the mob-griefing game rule when nothing is listening
     */
    public static boolean getMobGriefingEvent(Level level, Entity entity) {
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /**
     * Posts the special-spawn event Forge fires just before {@code Mob.finalizeSpawn}.
     *
     * @return {@code true} when a listener canceled the event and the mob should skip finalize
     */
    public static boolean doSpecialSpawn(Mob entity, LevelAccessor level, float x, float y, float z, @Nullable BaseSpawner spawner,
          MobSpawnType spawnReason) {
        return MinecraftForge.EVENT_BUS.post(new LivingSpawnEvent.SpecialSpawn(entity, level, x, y, z, spawner, spawnReason));
    }
}
