package com.technovision.advancedgenetics.common.item;

import com.technovision.advancedgenetics.registry.ItemRegistry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

public class ScalpelItem extends Item {

    public ScalpelItem(int durability) {
        super(ItemRegistry.properties().stacksTo(1).durability(durability));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        ItemCooldowns cooldownManager = user.getCooldowns();
        if (!user.level.isClientSide() && hand == InteractionHand.MAIN_HAND && !cooldownManager.isOnCooldown(stack.getItem())) {
            scrapeEntity(stack, user, entity, hand);
            cooldownManager.addCooldown(stack.getItem(), 10);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void scrapeEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        EntityType<?> type = entity.getType();
        RegistryObject<OrganicMatterItem> item = ItemRegistry.MATTER.get(type);

        if (item == null) {
            if (type == EntityType.SKELETON_HORSE) {
                item = ItemRegistry.MATTER.get(EntityType.SKELETON);
            }
            else if (type == EntityType.ZOMBIE_HORSE || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.ZOMBIE_VILLAGER) {
                item = ItemRegistry.MATTER.get(EntityType.ZOMBIE);
            }
            else if (type == EntityType.MULE) {
                item = ItemRegistry.MATTER.get(EntityType.HORSE);
            }
            else if (type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE) {
                item = ItemRegistry.MATTER.get(EntityType.PIG);
            }
            else if (type == EntityType.WITCH) {
                item = ItemRegistry.MATTER.get(EntityType.VILLAGER);
            }
        }
        if (item != null) {
            entity.spawnAtLocation(item.get());
            entity.hurt(DamageSource.playerAttack(user), 1.0f);
            stack.hurtAndBreak(1, user, (e) -> e.broadcastBreakEvent(hand));
        }
    }
}
