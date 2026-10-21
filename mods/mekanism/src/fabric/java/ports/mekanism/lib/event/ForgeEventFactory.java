/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.MinecraftForge;
import ports.mekanism.lib.common.util.BlockSnapshot;
import ports.mekanism.lib.event.entity.player.PlayerEvent;
import ports.mekanism.lib.event.level.BlockEvent;

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

    public static void firePlayerCraftingEvent(Player player, ItemStack crafted, Container craftMatrix) {
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, crafted, craftMatrix));
    }
}
