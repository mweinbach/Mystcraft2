/*
 * Mekanism 1.19.2 port - Forge Gender Mod capability. Loaded by name when that mod is present.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.common.integration.gender;

import com.wildfire.api.IGenderArmor;
import java.util.function.Consumer;
import mekanism.common.capabilities.ItemCapabilityWrapper.ItemCapability;
import mekanism.common.capabilities.resolver.BasicCapabilityResolver;
import mekanism.common.capabilities.resolver.ICapabilityResolver;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/**
 * The same capability token Wildfire's Gender Mod registers for {@link IGenderArmor}. Forge keys the token by that
 * type, so attaching it here is the capability the gender mod reads off a chestplate.
 */
public final class GenderArmorCapability extends ItemCapability implements IGenderArmor {

    private static final Capability<IGenderArmor> GENDER_ARMOR_CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    private final GenderArmorSettings settings;

    public GenderArmorCapability(GenderArmorSettings settings) {
        this.settings = settings;
    }

    public static void attach(ArmorItem item, GenderArmorSettings settings, Consumer<ItemCapability> addCapability) {
        addCapability.accept(new GenderArmorCapability(settings));
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

    @Override
    protected void gatherCapabilityResolvers(Consumer<ICapabilityResolver> consumer) {
        consumer.accept(BasicCapabilityResolver.constant(GENDER_ARMOR_CAPABILITY, this));
    }
}
