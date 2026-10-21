package mekanism.client.render.armor;

import net.minecraft.world.entity.EquipmentSlot;
import ports.mekanism.lib.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

public interface ISpecialGear extends IClientItemExtensions {

    @NotNull
    ICustomArmor getGearModel(EquipmentSlot slot);
}