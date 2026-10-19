package com.technovision.advancedgenetics.events;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.common.entity.FireballEntity;
import com.technovision.advancedgenetics.common.goal.FleePlayerGoal;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.mixin.MobAccessor;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles genes that trigger on rick clicking an item or block.
 * <p>
 * Loader adapters call these from equivalent hook points. Handlers returning {@code true} have
 * handled the action: the caller cancels it and, for interactions, reports success.
 *
 * @author TechnoVision
 */
public class GeneticsEvents {

    /**
     * Handles the "Eat Grass" gene
     */
    public static boolean onRightClickBlock(Player player, Level world, InteractionHand hand, BlockPos pos) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return false;
        if (world.getBlockState(pos).getBlock() != Blocks.GRASS_BLOCK) return false;
        if (!ComponentRegistry.getGenetics(player).hasGene(Genes.EAT_GRASS)) return false;
        if (player.getFoodData().needsFood()) {
            player.getFoodData().eat(1, 0.0f);
            world.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        }
        return true;
    }

    /**
     * Handles the "Milky", "Beelicious", "Meaty" and "Wooly" genes
     */
    public static boolean onEntityInteract(Player player, Level world, InteractionHand hand, Entity target) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return false;
        if (!(target instanceof Player clickedPlayer)) return false;

        PlayerGeneticsComponent component = ComponentRegistry.getGenetics(clickedPlayer);
        ItemStack stack = player.getMainHandItem();
        boolean handled = false;

        // Milk player
        if (stack.is(Items.BUCKET) && component.hasGene(Genes.MILKY)) {
            handled = true;
            if (!component.isOnCooldown("milky")) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.MILK_BUCKET)));
                component.addCooldown("milky", 15);
                player.playSound(SoundEvents.BUCKET_FILL, 1.0f, 1.0f);
            } else {
                sendCooldownMessage(player, Genes.MILKY);
            }
        }
        // Get honey from player
        if (stack.is(Items.GLASS_BOTTLE) && component.hasGene(Genes.BEELICIOUS)) {
            handled = true;
            if (!component.isOnCooldown("beelicious")) {
                stack.shrink(1);
                ItemStack honey = new ItemStack(Items.HONEY_BOTTLE);
                if (!player.getInventory().add(honey)) {
                    player.drop(honey, false);
                }
                component.addCooldown("beelicious", 300);
                player.playSound(SoundEvents.BEEHIVE_DRIP, 1.0f, 1.0f);
            } else {
                sendCooldownMessage(player, Genes.BEELICIOUS);
            }
        }
        // Shear porkchops off player
        if (stack.is(Items.SHEARS) && component.hasGene(Genes.MEATY)) {
            handled = true;
            if (!component.isOnCooldown("meaty")) {
                clickedPlayer.spawnAtLocation(new ItemStack(Items.PORKCHOP, 1));
                player.getMainHandItem().hurtAndBreak(1, player, (e) -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                component.addCooldown("meaty", 15);
                player.playSound(SoundEvents.MOOSHROOM_SHEAR, 1.0f, 1.0f);
            } else {
                sendCooldownMessage(player, Genes.MEATY);
            }
        }
        // Shear wool off player
        if (player.getMainHandItem().is(Items.SHEARS) && component.hasGene(Genes.WOOLY)) {
            handled = true;
            if (!component.isOnCooldown("wooly")) {
                clickedPlayer.spawnAtLocation(new ItemStack(Items.WHITE_WOOL, 1));
                player.getMainHandItem().hurtAndBreak(1, player, (e) -> e.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                component.addCooldown("wooly", 15);
                player.playSound(SoundEvents.SHEEP_SHEAR, 1.0f, 1.0f);
            } else {
                sendCooldownMessage(player, Genes.WOOLY);
            }
        }
        return handled;
    }

    /**
     * Handles "Explosive Exit", "Emerald Heart", and "Slimy" gene
     */
    public static void onPlayerDeath(ServerPlayer player) {
        Level world = player.getLevel();
        PlayerGeneticsComponent component = ComponentRegistry.getGenetics(player);
        if (component.hasGene(Genes.EXPLOSIVE_EXIT)) {
            // Explode on death
            if (player.getInventory().countItem(Items.GUNPOWDER) >= 5) {
                world.explode(player, player.getX(), player.getY(), player.getZ(), 3.0f, Explosion.BlockInteraction.BREAK);
            }
        }
        if (component.hasGene(Genes.EMERALD_HEART)) {
            // Drop emerald on death
            if (!component.isOnCooldown("emerald_heart")) {
                player.spawnAtLocation(new ItemStack(Items.EMERALD));
                component.addCooldown("emerald_heart", 300);
            }
        }
        if (component.hasGene(Genes.SLIMY)) {
            // Spawn slime
            Slime slime = new Slime(EntityType.SLIME, world);
            slime.setPos(player.position());
            slime.setSize(ThreadLocalRandom.current().nextInt(3), false);
            world.addFreshEntity(slime);
        }
    }

    /**
     * Handles "Wither Hit" and "Venom" gene. Applying either effect cancels the hit, as upstream does.
     */
    public static boolean onAttackEntity(Player player, Entity target) {
        if (player.level.isClientSide() || !(target instanceof LivingEntity livingEntity)) return false;
        PlayerGeneticsComponent component = ComponentRegistry.getGenetics(player);
        if (component.hasGene(Genes.WITHER_HIT) && !livingEntity.hasEffect(MobEffects.WITHER)) {
            // Apply wither affect for 1-5 seconds
            int seconds = ThreadLocalRandom.current().nextInt(5) + 1;
            livingEntity.addEffect(new MobEffectInstance(MobEffects.WITHER, 20*seconds, 0));
            return true;
        }
        if (component.hasGene(Genes.VENOM) && !livingEntity.hasEffect(MobEffects.POISON)) {
            // 5% chance to apply poison affect for 30 seconds
            if (ThreadLocalRandom.current().nextDouble() <= 0.05) {
                livingEntity.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 30, 0));
                return true;
            }
        }
        return false;
    }

    /**
     * Handles "Shoot Fireball" gene. Upstream only shoots when the gene is absent or disabled.
     */
    public static boolean onRightClickItem(Player player, Level world, InteractionHand hand) {
        if (world.isClientSide() || hand != InteractionHand.MAIN_HAND) return false;
        if (!ComponentRegistry.getGenetics(player).hasGene(Genes.SHOOT_FIREBALLS)) {
            // Shoots a fire charge if holding blaze rod
            if (player.getMainHandItem().is(Items.FIRE_CHARGE)) {
                if (!player.isCreative()) player.getMainHandItem().shrink(1);
                Vec3 v3 = player.getViewVector(1);
                FireballEntity fireballEntity = new FireballEntity(world, player, v3);
                world.addFreshEntity(fireballEntity);
                player.playSound(SoundEvents.FIRECHARGE_USE, 1.0f, 1.0f);
                return true;
            }
        }
        return false;
    }

    /**
     * Handles "Scare Creepers" and "Scare Skeletons", replacing upstream's goal-registration mixins.
     */
    public static void onEntityJoinLevel(Entity entity, Level world) {
        if (world.isClientSide()) return;
        if (entity instanceof Creeper creeper) {
            addFleeGoal(creeper, Genes.SCARE_CREEPERS, 6.0F);
        } else if (entity instanceof AbstractSkeleton skeleton) {
            addFleeGoal(skeleton, Genes.SCARE_SKELETONS, 10.0F);
        }
    }

    private static void addFleeGoal(PathfinderMob mob, Genes gene, float distance) {
        GoalSelector goals = ((MobAccessor) mob).advancedgenetics$getGoalSelector();
        boolean present = goals.getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof FleePlayerGoal);
        if (!present) {
            goals.addGoal(3, new FleePlayerGoal(mob, gene, distance, 1.0, 1.2));
        }
    }

    /**
     * Forwards server-side player damage to the gene handlers, replacing upstream's damage mixin.
     * Returns {@code true} when a gene negates the damage.
     */
    public static boolean onPlayerAttacked(ServerPlayer player, DamageSource source, float amount) {
        return DamageReceivedEvent.onDamageReceivedEvent(new DamageReceivedEvent(player, source, amount));
    }

    private static void sendCooldownMessage(Player player, Genes gene) {
        player.sendSystemMessage(Component.translatable("message."+ AdvancedGenetics.MOD_ID+".cooldown", "§7"+gene.getName()+"§f"));
    }
}
