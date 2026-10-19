package com.technovision.advancedgenetics.events;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.api.genetics.Genes;
import com.technovision.advancedgenetics.common.entity.FireballEntity;
import com.technovision.advancedgenetics.common.goal.FleePlayerGoal;
import com.technovision.advancedgenetics.component.PlayerGeneticsComponent;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles genes that trigger on rick clicking an item or block.
 *
 * @author TechnoVision
 */
public class GeneticsEvents {

    public static void registerEvents() {
        MinecraftForge.EVENT_BUS.addListener(GeneticsEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(GeneticsEvents::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(GeneticsEvents::onPlayerDeath);
        MinecraftForge.EVENT_BUS.addListener(GeneticsEvents::onAttackEntity);
        MinecraftForge.EVENT_BUS.addListener(GeneticsEvents::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(GeneticsEvents::onEntityJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(GeneticsEvents::onPlayerAttacked);
    }

    /**
     * Handles the "Eat Grass" gene
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level world = event.getLevel();
        if (world.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return;
        BlockPos pos = event.getPos();
        if (world.getBlockState(pos).getBlock() != Blocks.GRASS_BLOCK) return;
        if (!ComponentRegistry.getGenetics(player).hasGene(Genes.EAT_GRASS)) return;
        if (player.getFoodData().needsFood()) {
            player.getFoodData().eat(1, 0.0f);
            world.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * Handles the "Milky", "Beelicious", "Meaty" and "Wooly" genes
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof Player clickedPlayer)) return;

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
        if (handled) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Handles "Explosive Exit", "Emerald Heart", and "Slimy" gene
     */
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
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
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level.isClientSide() || !(event.getTarget() instanceof LivingEntity livingEntity)) return;
        PlayerGeneticsComponent component = ComponentRegistry.getGenetics(player);
        if (component.hasGene(Genes.WITHER_HIT) && !livingEntity.hasEffect(MobEffects.WITHER)) {
            // Apply wither affect for 1-5 seconds
            int seconds = ThreadLocalRandom.current().nextInt(5) + 1;
            livingEntity.addEffect(new MobEffectInstance(MobEffects.WITHER, 20*seconds, 0));
            event.setCanceled(true);
            return;
        }
        if (component.hasGene(Genes.VENOM) && !livingEntity.hasEffect(MobEffects.POISON)) {
            // 5% chance to apply poison affect for 30 seconds
            if (ThreadLocalRandom.current().nextDouble() <= 0.05) {
                livingEntity.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 30, 0));
                event.setCanceled(true);
            }
        }
    }

    /**
     * Handles "Shoot Fireball" gene. Upstream only shoots when the gene is absent or disabled.
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level world = event.getLevel();
        if (world.isClientSide() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!ComponentRegistry.getGenetics(player).hasGene(Genes.SHOOT_FIREBALLS)) {
            // Shoots a fire charge if holding blaze rod
            if (player.getMainHandItem().is(Items.FIRE_CHARGE)) {
                if (!player.isCreative()) player.getMainHandItem().shrink(1);
                Vec3 v3 = player.getViewVector(1);
                FireballEntity fireballEntity = new FireballEntity(world, player, v3);
                world.addFreshEntity(fireballEntity);
                player.playSound(SoundEvents.FIRECHARGE_USE, 1.0f, 1.0f);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    /**
     * Handles "Scare Creepers" and "Scare Skeletons", replacing upstream's goal-registration mixins.
     */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Creeper creeper) {
            addFleeGoal(creeper, Genes.SCARE_CREEPERS, 6.0F);
        } else if (event.getEntity() instanceof AbstractSkeleton skeleton) {
            addFleeGoal(skeleton, Genes.SCARE_SKELETONS, 10.0F);
        }
    }

    private static void addFleeGoal(PathfinderMob mob, Genes gene, float distance) {
        boolean present = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof FleePlayerGoal);
        if (!present) {
            mob.goalSelector.addGoal(3, new FleePlayerGoal(mob, gene, distance, 1.0, 1.2));
        }
    }

    /**
     * Forwards server-side player damage to the gene handlers, replacing upstream's damage mixin.
     */
    public static void onPlayerAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (DamageReceivedEvent.onDamageReceivedEvent(new DamageReceivedEvent(player, event.getSource(), event.getAmount()))) {
                event.setCanceled(true);
            }
        }
    }

    private static void sendCooldownMessage(Player player, Genes gene) {
        player.sendSystemMessage(Component.translatable("message."+ AdvancedGenetics.MOD_ID+".cooldown", "§7"+gene.getName()+"§f"));
    }
}
