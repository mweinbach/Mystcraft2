/*
 * Mekanism 1.19.2 port - Fabric Trinkets adapter. The shared facade loads this class by name.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.common.integration.curios;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketsApi;
import dev.emi.trinkets.api.client.TrinketRenderer;
import dev.emi.trinkets.api.client.TrinketRendererRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import mekanism.client.render.armor.ICustomArmor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import ports.mekanism.lib.api.distmarker.Dist;
import ports.mekanism.lib.eventbus.api.IEventBus;
import ports.mekanism.lib.fml.event.lifecycle.FMLClientSetupEvent;
import ports.mekanism.lib.fml.loading.FMLEnvironment;
import ports.mekanism.lib.items.IItemHandler;

/**
 * Trinkets is the Fabric equivalent of Curios. A slot id is {@code group/name}, which is what the mode packet sends
 * back. Trinkets slots come from data in {@code data/mekanism/trinkets}; there is no IMC message to send.
 */
public final class FabricTrinkets {

    private FabricTrinkets() {
    }

    public static void addListeners(IEventBus bus) {
        // The renderer references client types. Link it only on the client so a dedicated server never loads it.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            bus.addListener((FMLClientSetupEvent event) -> registerRenderers());
        }
    }

    private static void registerRenderers() {
        // The supplier runs on first render. Gear models are not ready while renderers are being registered.
        CuriosIntegration.forEachSpecialGear((armor, model) -> TrinketRendererRegistry.registerRenderer(armor, new GearRenderer(model)));
    }

    public static void sendIMC() {
    }

    public static void install() {
        CuriosIntegration.install(new AccessoryInventory() {
            @Override
            public Optional<? extends IItemHandler> inventory(LivingEntity entity) {
                return TrinketsApi.getTrinketComponent(entity).map(component -> {
                    List<ItemStack> stacks = new ArrayList<>();
                    component.forEach((slot, stack) -> stacks.add(stack));
                    return new AccessoryItemHandler(stacks);
                });
            }

            @Override
            public Optional<AccessoryResult> findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
                return TrinketsApi.getTrinketComponent(entity).flatMap(component -> component.getEquipped(filter).stream()
                      .findFirst()
                      .map(pair -> new AccessoryResult(slotId(pair.getA()), pair.getA().index(), pair.getB())));
            }

            @Override
            public ItemStack stack(LivingEntity entity, String slotType, int index) {
                return TrinketsApi.getTrinketComponent(entity).map(component -> component.getEquipped(stack -> true).stream()
                            .filter(pair -> slotId(pair.getA()).equals(slotType) && pair.getA().index() == index)
                            .map(pair -> pair.getB())
                            .findFirst()
                            .orElse(ItemStack.EMPTY))
                      .orElse(ItemStack.EMPTY);
            }
        });
    }

    private static String slotId(SlotReference slot) {
        return slot.inventory().getSlotType().getGroup() + "/" + slot.inventory().getSlotType().getName();
    }

    /**
     * Draws the same gear model Curios draws. The model supplier runs on the first render, which is when upstream's
     * Curios supplier runs.
     */
    private static final class GearRenderer implements TrinketRenderer {

        private final Supplier<ICustomArmor> model;
        private ICustomArmor resolved;

        private GearRenderer(Supplier<ICustomArmor> model) {
            this.model = model;
        }

        @Override
        public void render(ItemStack stack, SlotReference slotReference, EntityModel<? extends LivingEntity> contextModel, PoseStack matrices,
              MultiBufferSource vertexConsumers, int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta,
              float animationProgress, float headYaw, float headPitch) {
            if (contextModel instanceof HumanoidModel<?> humanoidModel) {
                if (resolved == null) {
                    resolved = model.get();
                }
                resolved.render(humanoidModel, matrices, vertexConsumers, light, OverlayTexture.NO_OVERLAY, tickDelta, stack.hasFoil(), entity, stack);
            }
        }
    }
}
