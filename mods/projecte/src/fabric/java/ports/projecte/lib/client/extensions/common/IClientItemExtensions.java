/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.extensions.common;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client behavior an item supplies through {@code initializeClient}. Mixins and Fabric's renderer registries consult
 * it where vanilla renders items, armor and arms.
 */
public interface IClientItemExtensions {

    IClientItemExtensions DEFAULT = new IClientItemExtensions() {
    };

    static IClientItemExtensions of(ItemStack stack) {
        return of(stack.getItem());
    }

    static IClientItemExtensions of(Item item) {
        return Cache.BY_ITEM.computeIfAbsent(item, i -> {
            IClientItemExtensions[] holder = {DEFAULT};
            i.projecte$initializeClient(extensions -> holder[0] = extensions);
            return holder[0];
        });
    }

    @Nullable
    default Font getFont(ItemStack stack, FontContext context) {
        return null;
    }

    @Nullable
    default HumanoidModel.ArmPose getArmPose(LivingEntity entityLiving, InteractionHand hand, ItemStack itemStack) {
        return null;
    }

    /**
     * @return {@code true} if the hand transform was applied and vanilla's should be skipped
     */
    default boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand, float partialTick, float equipProcess,
          float swingProcess) {
        return false;
    }

    default HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
        return original;
    }

    default Model getGenericArmorModel(LivingEntity livingEntity, ItemStack itemStack, EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
        HumanoidModel<?> replacement = getHumanoidArmorModel(livingEntity, itemStack, equipmentSlot, original);
        if (replacement != original) {
            copyModelProperties(original, replacement);
            return replacement;
        }
        return original;
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> void copyModelProperties(HumanoidModel<T> original, HumanoidModel<?> replacement) {
        original.copyPropertiesTo((HumanoidModel<T>) replacement);
    }

    default void renderHelmetOverlay(ItemStack stack, Player player, int width, int height, float partialTick) {
    }

    default BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return Minecraft.getInstance().getItemRenderer().blockEntityRenderer;
    }

    enum FontContext {
        ITEM_COUNT,
        TOOLTIP,
        SELECTED_ITEM_NAME
    }

    final class Cache {

        private static final Map<Item, IClientItemExtensions> BY_ITEM = new ConcurrentHashMap<>();

        private Cache() {
        }
    }
}
