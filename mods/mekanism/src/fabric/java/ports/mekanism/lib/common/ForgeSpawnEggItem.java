/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.SpawnEggItem;

/**
 * A spawn egg for an entity type that is registered after the item. Vanilla's egg lookup and dispenser behavior are
 * pointed at the supplier by the port's mixins once registration completes.
 */
public class ForgeSpawnEggItem extends SpawnEggItem {

    private final Supplier<? extends EntityType<? extends Mob>> typeSupplier;

    public ForgeSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type, int backgroundColor, int highlightColor, Properties props) {
        super(null, backgroundColor, highlightColor, props);
        this.typeSupplier = type;
    }

    public EntityType<?> getDefaultType() {
        return typeSupplier.get();
    }
}
