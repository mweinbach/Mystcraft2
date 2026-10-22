/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.tools.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Locale;
import mekanism.common.registration.impl.ItemRegistryObject;
import mekanism.tools.client.render.item.RenderMekanismShieldItem;
import mekanism.tools.common.item.ItemMekanismShield;
import mekanism.tools.common.registries.ToolsItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import ports.mekanism.lib.client.extensions.common.IClientItemExtensions;

/**
 * Tools client entry. Shield items are Forge built-in renderers ({@code builtin/entity}, parented on the vanilla
 * shield). Fabric draws them through {@link BuiltinItemRendererRegistry} with the same BEWLR Forge calls. Refined
 * glowstone armor is the vanilla armor layer drawn fullbright: {@link ArmorRenderer} calls
 * {@code getGenericArmorModel}, which wraps the layer in {@code GlowArmor}.
 */
public final class ToolsFabricClient implements ClientModInitializer {

    private static final List<ItemRegistryObject<ItemMekanismShield>> SHIELDS = List.of(
          ToolsItems.BRONZE_SHIELD,
          ToolsItems.LAPIS_LAZULI_SHIELD,
          ToolsItems.OSMIUM_SHIELD,
          ToolsItems.REFINED_GLOWSTONE_SHIELD,
          ToolsItems.REFINED_OBSIDIAN_SHIELD,
          ToolsItems.STEEL_SHIELD
    );

    private static HumanoidModel<?> outerFallback;
    private static HumanoidModel<?> innerFallback;

    @Override
    public void onInitializeClient() {
        BuiltinItemRendererRegistry.DynamicItemRenderer shieldRenderer = (stack, mode, matrices, consumers, light, overlay) ->
              RenderMekanismShieldItem.RENDERER.renderByItem(stack, mode, matrices, consumers, light, overlay);
        for (ItemRegistryObject<ItemMekanismShield> shield : SHIELDS) {
            BuiltinItemRendererRegistry.INSTANCE.register(shield.asItem(), shieldRenderer);
        }
        ArmorRenderer.register(ToolsFabricClient::renderGlowArmor,
              ToolsItems.REFINED_GLOWSTONE_HELMET.asItem(),
              ToolsItems.REFINED_GLOWSTONE_CHESTPLATE.asItem(),
              ToolsItems.REFINED_GLOWSTONE_LEGGINGS.asItem(),
              ToolsItems.REFINED_GLOWSTONE_BOOTS.asItem());
    }

    /**
     * Fabric cancels vanilla's armor piece when a renderer is registered, so this repeats the setup Forge's
     * {@code HumanoidArmorLayer} does before {@code getGenericArmorModel}: copy the entity pose, show the slot's
     * parts, then draw the wrapped model with the material texture. {@code GlowArmor} replaces the light with full
     * brightness. The port's OBJ loader still has no emissive faces; this armor is not an OBJ model.
     */
    private static void renderGlowArmor(PoseStack poseStack, MultiBufferSource buffers,
          ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) {
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getSlot() != slot) {
            return;
        }
        HumanoidModel<?> armorModel = findArmorModel(entity, slot);
        if (armorModel == null) {
            armorModel = fallbackModel(slot);
        }
        copyProperties(contextModel, armorModel);
        setPartVisibility(armorModel, slot);
        Model model = IClientItemExtensions.of(stack).getGenericArmorModel(entity, stack, slot, armorModel);
        ArmorRenderer.renderPart(poseStack, buffers, light, stack, model, armorTexture(armor, slot));
    }

    private static HumanoidModel<?> findArmorModel(LivingEntity entity, EquipmentSlot slot) {
        EntityRenderer<? super LivingEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            return null;
        }
        HumanoidArmorLayer<?, ?, ?> fallback = null;
        for (Object layer : living.layers) {
            if (layer instanceof HumanoidArmorLayer<?, ?, ?> armor) {
                if (layer.getClass() == HumanoidArmorLayer.class) {
                    return armorModel(armor, slot);
                }
                if (fallback == null) {
                    fallback = armor;
                }
            }
        }
        return fallback == null ? null : armorModel(fallback, slot);
    }

    private static HumanoidModel<?> armorModel(HumanoidArmorLayer<?, ?, ?> armor, EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS ? armor.innerModel : armor.outerModel;
    }

    private static HumanoidModel<?> fallbackModel(EquipmentSlot slot) {
        boolean legs = slot == EquipmentSlot.LEGS;
        HumanoidModel<?> cached = legs ? innerFallback : outerFallback;
        if (cached != null) {
            return cached;
        }
        ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(legs ? ModelLayers.PLAYER_INNER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR);
        HumanoidModel<?> model = new HumanoidModel<>(root);
        if (legs) {
            innerFallback = model;
        } else {
            outerFallback = model;
        }
        return model;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void copyProperties(HumanoidModel<?> from, HumanoidModel<?> to) {
        ((HumanoidModel) from).copyPropertiesTo(to);
    }

    private static void setPartVisibility(HumanoidModel<?> model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }

    private static ResourceLocation armorTexture(ArmorItem armor, EquipmentSlot slot) {
        String name = armor.getMaterial().getName();
        int separator = name.indexOf(':');
        String namespace = separator == -1 ? "minecraft" : name.substring(0, separator);
        String path = separator == -1 ? name : name.substring(separator + 1);
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return new ResourceLocation(namespace, String.format(Locale.ROOT, "textures/models/armor/%s_layer_%d.png", path, layer));
    }
}
