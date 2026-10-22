package mekanism.common.integration.gender;

import java.util.function.Consumer;
import mekanism.common.Mekanism;
import mekanism.common.capabilities.ItemCapabilityWrapper.ItemCapability;
import mekanism.common.registries.MekanismItems;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

/**
 * Attaches upstream's Gender Mod settings when that mod is loaded. The loader class is resolved by name so this
 * helper does not reference the Gender Mod until then.
 */
public final class GenderCapabilityHelper {

    private static final String ADAPTER = "mekanism.common.integration.gender.GenderArmorCapability";

    private GenderCapabilityHelper() {
    }

    public static GenderArmorSettings coreChest(ArmorItem item) {
        if (item == MekanismItems.HAZMAT_GOWN.asItem()) {
            return GenderArmorSettings.HAZMAT;
        } else if (item == MekanismItems.JETPACK.asItem() || item == MekanismItems.SCUBA_TANK.asItem()) {
            return GenderArmorSettings.OPEN_FRONT;
        } else if (item == MekanismItems.ARMORED_JETPACK.asItem() || item == MekanismItems.MEKASUIT_BODYARMOR.asItem()) {
            return GenderArmorSettings.HIDES_BREASTS;
        }
        return null;
    }

    public static void registerCore() {
        register(MekanismItems.HAZMAT_GOWN.asItem());
        register(MekanismItems.JETPACK.asItem());
        register(MekanismItems.SCUBA_TANK.asItem());
        register(MekanismItems.ARMORED_JETPACK.asItem());
        register(MekanismItems.MEKASUIT_BODYARMOR.asItem());
    }

    private static void register(Item item) {
        if (item instanceof ArmorItem armor) {
            addGenderCapability(armor, ignored -> {});
        }
    }

    public static void addGenderCapability(ArmorItem item, Consumer<ItemCapability> addCapability) {
        if (Mekanism.hooks.WildfireGenderModLoaded && item.getSlot() == EquipmentSlot.CHEST) {
            GenderArmorSettings settings = coreChest(item);
            if (settings != null) {
                attach(item, settings, addCapability);
            }
        }
    }

    public static void attach(ArmorItem item, GenderArmorSettings settings, Consumer<ItemCapability> addCapability) {
        try {
            Class.forName(ADAPTER, true, GenderCapabilityHelper.class.getClassLoader())
                  .getMethod("attach", ArmorItem.class, GenderArmorSettings.class, Consumer.class)
                  .invoke(null, item, settings, addCapability);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Wildfire's Gender Mod is loaded but Mekanism's gender adapter is missing", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Wildfire's Gender Mod is loaded but Mekanism's gender adapter failed", e);
        }
    }
}
