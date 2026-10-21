package mekanism.common.inventory.container.type;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import ports.mekanism.lib.network.IContainerFactory;

public abstract class BaseMekanismContainerType<T, CONTAINER extends AbstractContainerMenu, FACTORY> extends MenuType<CONTAINER> {

    protected final FACTORY mekanismConstructor;
    protected final Class<T> type;

    protected BaseMekanismContainerType(Class<T> type, FACTORY mekanismConstructor, IContainerFactory<CONTAINER> constructor) {
        super(constructor);
        this.type = type;
        this.mekanismConstructor = mekanismConstructor;
    }
}