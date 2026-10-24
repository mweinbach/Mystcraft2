/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin;

import com.google.common.collect.Multimap;
import moze_intel.projecte.gameObjs.items.armor.GemFeet;
import moze_intel.projecte.gameObjs.items.tools.PEHammer;
import moze_intel.projecte.gameObjs.items.tools.PEKatar;
import moze_intel.projecte.gameObjs.items.tools.PEMorningStar;
import moze_intel.projecte.gameObjs.items.tools.PESword;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import ports.projecte.lib.common.extensions.IForgeItem;

/** Fabric API's stack-aware attribute hook answered by the ProjectE items overriding Forge's. */
@Mixin({PEHammer.class, PEKatar.class, PEMorningStar.class, PESword.class, GemFeet.class})
public abstract class ChargeAttributesMixin implements FabricItem {

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(ItemStack stack, EquipmentSlot slot) {
        return ((IForgeItem) this).projecte$getAttributeModifiers(slot, stack);
    }
}
