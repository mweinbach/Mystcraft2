/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import mekanism.client.render.armor.ISpecialGear;
import mekanism.client.render.layer.MekanismArmorLayer;
import mekanism.common.item.gear.GearArmorPieces;
import mekanism.common.registration.impl.ItemRegistryObject;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.client.event.EntityRenderersEvent;
import ports.mekanism.lib.client.extensions.common.IClientItemExtensions;

/**
 * Worn gear on Fabric. {@link ArmorRenderer} replaces vanilla's armor piece, then calls the same
 * {@code ICustomArmor} Forge's {@code MekanismArmorLayer} calls. That layer is still attached by the shared
 * {@code AddLayers} listener; it is removed here so the model is drawn once. Hazmat has no custom model: it is the
 * vanilla armor mesh with the namespaced hazmat texture. The HDPE elytra stays on the elytra layer.
 */
public final class FabricArmorRendering {

    private static HumanoidModel<?> outerFallback;
    private static HumanoidModel<?> innerFallback;

    private FabricArmorRendering() {
    }

    public static void register() {
        Item[] custom = items(GearArmorPieces.customModels());
        for (Item item : custom) {
            if (!(item instanceof ArmorItem armor)) {
                throw new IllegalStateException("Custom gear armor is not an armor item: " + item);
            }
            if (!(IClientItemExtensions.of(item) instanceof ISpecialGear gear) || gear.getGearModel(armor.getSlot()) == null) {
                throw new IllegalStateException("Custom gear armor has no model: " + item);
            }
        }
        ArmorRenderer.register(FabricArmorRendering::renderCustom, custom);
        ArmorRenderer.register(FabricArmorRendering::renderHazmat, items(GearArmorPieces.hazmat()));
    }

    /**
     * Runs after the shared listener has added {@link MekanismArmorLayer}. Fabric's armor renderer already drew
     * those pieces from the vanilla armor layer.
     */
    public static void dropDuplicateArmorLayer(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            removeArmorLayer(event.getSkin(skin));
        }
        for (EntityRenderer<?> renderer : Minecraft.getInstance().getEntityRenderDispatcher().renderers.values()) {
            removeArmorLayer(renderer);
        }
    }

    private static void removeArmorLayer(@Nullable EntityRenderer<?> renderer) {
        if (renderer instanceof LivingEntityRenderer<?, ?> living) {
            living.layers.removeIf(layer -> layer instanceof MekanismArmorLayer<?, ?, ?>);
        }
    }

    /**
     * Fabric passes the entity model, which already has this frame's pose. The armor model is the one
     * {@code MekanismArmorLayer} copies that pose onto before {@code ICustomArmor.render}.
     */
    private static void renderCustom(PoseStack poseStack, MultiBufferSource buffers, ItemStack stack, LivingEntity entity,
          EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) {
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getSlot() != slot) {
            return;
        }
        if (!(IClientItemExtensions.of(stack) instanceof ISpecialGear gear)) {
            return;
        }
        HumanoidModel<?> armorModel = armorModel(entity, slot);
        copyProperties(contextModel, armorModel);
        setPartVisibility(armorModel, slot);
        gear.getGearModel(slot).render(armorModel, poseStack, buffers, light, OverlayTexture.NO_OVERLAY,
              Minecraft.getInstance().getFrameTime(), stack.hasFoil(), entity, stack);
    }

    private static void renderHazmat(PoseStack poseStack, MultiBufferSource buffers, ItemStack stack, LivingEntity entity,
          EquipmentSlot slot, int light, HumanoidModel<LivingEntity> contextModel) {
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getSlot() != slot) {
            return;
        }
        HumanoidModel<?> armorModel = armorModel(entity, slot);
        copyProperties(contextModel, armorModel);
        setPartVisibility(armorModel, slot);
        ArmorRenderer.renderPart(poseStack, buffers, light, stack, armorModel,
              new ResourceLocation(GearArmorPieces.vanillaArmorTexture(armor.getMaterial(), slot)));
    }

    private static Item[] items(List<ItemRegistryObject<? extends ArmorItem>> pieces) {
        List<Item> items = new ArrayList<>(pieces.size());
        for (ItemRegistryObject<? extends ArmorItem> piece : pieces) {
            items.add(piece.asItem());
        }
        return items.toArray(Item[]::new);
    }

    private static HumanoidModel<?> armorModel(LivingEntity entity, EquipmentSlot slot) {
        HumanoidModel<?> model = findArmorModel(entity, slot);
        return model == null ? fallbackModel(slot) : model;
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
                    return modelFor(armor, slot);
                }
                if (fallback == null) {
                    fallback = armor;
                }
            }
        }
        return fallback == null ? null : modelFor(fallback, slot);
    }

    private static HumanoidModel<?> modelFor(HumanoidArmorLayer<?, ?, ?> armor, EquipmentSlot slot) {
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
}
