package mekanism.common.integration.curios;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import mekanism.client.render.armor.ICustomArmor;
import mekanism.client.render.armor.ISpecialGear;
import mekanism.common.Mekanism;
import mekanism.common.registries.MekanismItems;
import mekanism.common.util.RegistryUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Looks up items worn in Curios slots on Forge and Trinkets slots on Fabric.
 * <p>
 * Upstream does not tag items into the body slot. Anything already in an accessory slot is visible to the jetpack
 * lookup, the mode key, the HUD, the charge pad and the MekaSuit charge-distribution module. The jetpack and the
 * armored jetpack are the items that get an accessory renderer. The portable QIO dashboard is not one of those
 * items: it is returned by the slot scan, and it is not a jetpack, a mode item or a HUD provider.
 */
public final class CuriosIntegration {

    private static final String FORGE = "mekanism.common.integration.curios.ForgeCurios";
    private static final String FABRIC = "mekanism.common.integration.curios.FabricTrinkets";

    private static AccessoryInventory inventory = AccessoryInventory.NONE;

    private CuriosIntegration() {
    }

    public static void install(AccessoryInventory installed) {
        inventory = installed == null ? AccessoryInventory.NONE : installed;
    }

    public static void addListeners(IEventBus bus) {
        invoke("addListeners", new Class<?>[]{IEventBus.class}, bus);
    }

    public static void sendIMC() {
        invoke("sendIMC", new Class<?>[0]);
    }

    public static void installNative() {
        invoke("install", new Class<?>[0]);
    }

    /**
     * Jetpack and armored jetpack, when their client extensions are {@link ISpecialGear}. The model supplier runs
     * when the loader builds the renderer.
     */
    public static void forEachSpecialGear(BiConsumer<ArmorItem, Supplier<ICustomArmor>> register) {
        registerGear(register, MekanismItems.JETPACK, MekanismItems.ARMORED_JETPACK);
    }

    private static void registerGear(BiConsumer<ArmorItem, Supplier<ICustomArmor>> register, ItemLike... items) {
        for (ItemLike item : items) {
            if (item.asItem() instanceof ArmorItem armor && IClientItemExtensions.of(armor) instanceof ISpecialGear gear) {
                register.accept(armor, () -> gear.getGearModel(armor.getSlot()));
            } else {
                Mekanism.logger.warn("Attempted to register an accessory renderer for non-special gear item: {}.", RegistryUtils.getName(item.asItem()));
            }
        }
    }

    public static Optional<? extends IItemHandler> getCuriosInventory(LivingEntity entity) {
        if (!Mekanism.hooks.CuriosLoaded || entity == null) {
            return Optional.empty();
        }
        return inventory.inventory(entity);
    }

    public static Optional<AccessoryResult> findFirstCurioAsResult(@NotNull LivingEntity entity, Predicate<ItemStack> filter) {
        if (!Mekanism.hooks.CuriosLoaded) {
            return Optional.empty();
        }
        return inventory.findFirst(entity, filter);
    }

    public static ItemStack findFirstCurio(@NotNull LivingEntity entity, Predicate<ItemStack> filter) {
        return findFirstCurioAsResult(entity, filter).map(AccessoryResult::stack).orElse(ItemStack.EMPTY);
    }

    public static ItemStack getCurioStack(@NotNull LivingEntity entity, String slotType, int slot) {
        if (!Mekanism.hooks.CuriosLoaded) {
            return ItemStack.EMPTY;
        }
        return inventory.stack(entity, slotType, slot);
    }

    private static void invoke(String method, Class<?>[] parameters, Object... arguments) {
        ClassLoader loader = CuriosIntegration.class.getClassLoader();
        for (String name : new String[]{FORGE, FABRIC}) {
            try {
                Class<?> type = Class.forName(name, true, loader);
                Method target = type.getMethod(method, parameters);
                target.invoke(null, arguments);
                return;
            } catch (ClassNotFoundException ignored) {
                // The other loader's adapter is the one in this JAR.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Curios or Trinkets is loaded but Mekanism's accessory adapter failed", e);
            }
        }
        throw new IllegalStateException("Curios or Trinkets is loaded but Mekanism's accessory adapter is missing");
    }
}
