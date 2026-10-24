/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event.entity.player;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.event.entity.living.LivingEvent;
import ports.projecte.lib.eventbus.api.Cancelable;

public class PlayerEvent extends LivingEvent {

    private final Player player;

    public PlayerEvent(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public Player getEntity() {
        return player;
    }

    @Cancelable
    public static class BreakSpeed extends PlayerEvent {

        private final BlockState state;
        private final float originalSpeed;
        private final Optional<BlockPos> pos;
        private float newSpeed;

        public BreakSpeed(Player player, BlockState state, float original, @Nullable BlockPos pos) {
            super(player);
            this.state = state;
            this.originalSpeed = original;
            this.newSpeed = original;
            this.pos = Optional.ofNullable(pos);
        }

        public BlockState getState() {
            return state;
        }

        public float getOriginalSpeed() {
            return originalSpeed;
        }

        public float getNewSpeed() {
            return newSpeed;
        }

        public void setNewSpeed(float newSpeed) {
            this.newSpeed = newSpeed;
        }

        public Optional<BlockPos> getPosition() {
            return pos;
        }
    }

    public static class Clone extends PlayerEvent {

        private final Player original;
        private final boolean wasDeath;

        public Clone(Player newPlayer, Player oldPlayer, boolean wasDeath) {
            super(newPlayer);
            this.original = oldPlayer;
            this.wasDeath = wasDeath;
        }

        public Player getOriginal() {
            return original;
        }

        public boolean isWasDeath() {
            return wasDeath;
        }
    }

    public static class StartTracking extends PlayerEvent {

        private final Entity target;

        public StartTracking(Player player, Entity target) {
            super(player);
            this.target = target;
        }

        public Entity getTarget() {
            return target;
        }
    }

    public static class ItemCraftedEvent extends PlayerEvent {

        private final ItemStack crafting;
        private final Container craftMatrix;

        public ItemCraftedEvent(Player player, ItemStack crafted, Container craftMatrix) {
            super(player);
            this.crafting = crafted;
            this.craftMatrix = craftMatrix;
        }

        public ItemStack getCrafting() {
            return crafting;
        }

        public Container getInventory() {
            return craftMatrix;
        }
    }

    public static class ItemSmeltedEvent extends PlayerEvent {

        private final ItemStack smelting;

        public ItemSmeltedEvent(Player player, ItemStack smelting) {
            super(player);
            this.smelting = smelting;
        }

        public ItemStack getSmelting() {
            return smelting;
        }
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {

        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerLoggedOutEvent extends PlayerEvent {

        public PlayerLoggedOutEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerRespawnEvent extends PlayerEvent {

        private final boolean endConquered;

        public PlayerRespawnEvent(Player player, boolean endConquered) {
            super(player);
            this.endConquered = endConquered;
        }

        public boolean isEndConquered() {
            return endConquered;
        }
    }

    public static class PlayerChangedDimensionEvent extends PlayerEvent {

        private final ResourceKey<Level> fromDim;
        private final ResourceKey<Level> toDim;

        public PlayerChangedDimensionEvent(Player player, ResourceKey<Level> fromDim, ResourceKey<Level> toDim) {
            super(player);
            this.fromDim = fromDim;
            this.toDim = toDim;
        }

        public ResourceKey<Level> getFrom() {
            return fromDim;
        }

        public ResourceKey<Level> getTo() {
            return toDim;
        }
    }
}
