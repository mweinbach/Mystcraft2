package mekanism.common.integration.curios;

import net.minecraft.world.item.ItemStack;

/**
 * One occupied accessory slot. Curios reports its slot identifier; Trinkets reports {@code group/name}.
 */
public record AccessoryResult(String slotType, int index, ItemStack stack) {
}
