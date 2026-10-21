/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ports.mekanism.lib.common.capabilities.AttachedCapabilities;
import ports.mekanism.lib.common.extensions.IForgeItemStack;

/**
 * Gives item stacks the capabilities of their item. Forge creates them with the stack; here they are created the
 * first time they are asked for, which is the same for Mekanism's item capabilities as they keep their state in the
 * stack's tag.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements IForgeItemStack, AttachedCapabilities.Holder {

    @Unique
    @Nullable
    private AttachedCapabilities mekanism$capabilities;

    @Override
    public AttachedCapabilities mekanism$capabilities() {
        if (mekanism$capabilities == null) {
            ItemStack self = (ItemStack) (Object) this;
            mekanism$capabilities = AttachedCapabilities.of(self.getItem().initCapabilities(self, null));
        }
        return mekanism$capabilities;
    }
}
