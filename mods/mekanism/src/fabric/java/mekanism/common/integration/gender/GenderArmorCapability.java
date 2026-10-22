/*
 * Mekanism 1.19.2 port - Fabric Gender Mod registration. Loaded by name when that mod is present.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.common.integration.gender;

import com.wildfire.api.IGenderArmor;
import com.wildfire.api.WildfireAPI;
import java.util.Objects;
import java.util.function.Consumer;
import mekanism.common.capabilities.ItemCapabilityWrapper.ItemCapability;
import net.minecraft.world.item.ArmorItem;

/**
 * Fabric's Gender Mod has no capability. {@link WildfireAPI#addGenderArmor} is how a chestplate reports the same
 * four values the Forge capability reports. The consumer is the Forge wrapper hook and is unused here.
 */
public final class GenderArmorCapability implements IGenderArmor {

    private final GenderArmorSettings settings;

    public GenderArmorCapability(GenderArmorSettings settings) {
        this.settings = settings;
    }

    public static void attach(ArmorItem item, GenderArmorSettings settings, Consumer<ItemCapability> addCapability) {
        Objects.requireNonNull(addCapability);
        WildfireAPI.addGenderArmor(item, new GenderArmorCapability(settings));
    }

    @Override
    public boolean coversBreasts() {
        return settings.coversBreasts();
    }

    @Override
    public boolean alwaysHidesBreasts() {
        return settings.alwaysHidesBreasts();
    }

    @Override
    public float physicsResistance() {
        return settings.physicsResistance();
    }

    @Override
    public float tightness() {
        return settings.tightness();
    }
}
