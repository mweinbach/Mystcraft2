/*
 * Mekanism 1.19.2 port - Forge Curios adapter. The shared facade loads this class by name.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.common.integration.curios;

import java.util.Optional;
import java.util.function.Predicate;
import mekanism.client.render.MekanismCurioRenderer;
import mekanism.common.integration.MekanismHooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypePreset;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * Curios 1.19.2. The body slot is the one upstream registers. Items are not added to {@code curios:body}; a stack
 * already in a slot is what the jetpack lookup, mode key, HUD and chargers see.
 */
public final class ForgeCurios {

    private ForgeCurios() {
    }

    public static void addListeners(IEventBus bus) {
        // The renderer class references client types. Link it only on the client so a dedicated server never loads it.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            bus.addListener((FMLClientSetupEvent event) -> registerRenderers());
        }
    }

    private static void registerRenderers() {
        CuriosIntegration.forEachSpecialGear((armor, model) -> CuriosRendererRegistry.register(armor, () -> new MekanismCurioRenderer(model.get())));
    }

    public static void sendIMC() {
        InterModComms.sendTo(MekanismHooks.CURIOS_MODID, "register_type", () -> SlotTypePreset.BODY.getMessageBuilder().build());
    }

    public static void install() {
        CuriosIntegration.install(new AccessoryInventory() {
            @Override
            public Optional<? extends IItemHandler> inventory(LivingEntity entity) {
                return CuriosApi.getCuriosHelper().getEquippedCurios(entity).resolve();
            }

            @Override
            public Optional<AccessoryResult> findFirst(LivingEntity entity, Predicate<ItemStack> filter) {
                return CuriosApi.getCuriosHelper().findFirstCurio(entity, filter)
                      .map(result -> new AccessoryResult(result.slotContext().identifier(), result.slotContext().index(), result.stack()));
            }

            @Override
            public ItemStack stack(LivingEntity entity, String slotType, int index) {
                return CuriosApi.getCuriosHelper().getCuriosHandler(entity)
                      .resolve()
                      .flatMap(handler -> handler.getStacksHandler(slotType))
                      .map(handler -> handler.getStacks().getStackInSlot(index))
                      .orElse(ItemStack.EMPTY);
            }
        });
    }
}
