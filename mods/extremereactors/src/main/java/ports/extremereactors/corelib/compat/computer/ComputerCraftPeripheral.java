/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.computer;

import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Offers a {@link MultiblockComputerPeripheral} to ComputerCraft. CC: Tweaked on Forge and CC: Restitched on Fabric
 * share this part of their API; each loader adapter only registers the provider.
 *
 * <p>Methods that change the machine run on the server thread. The others answer straight from the computer
 * thread, so reading a value does not cost the script a tick.</p>
 */
public final class ComputerCraftPeripheral
        implements IDynamicPeripheral {

    /**
     * @return the peripheral of the computer port at the given position, or null if there is none
     */
    @Nullable
    public static ComputerCraftPeripheral find(final Level level, final BlockPos position, final Direction side) {
        return level.getBlockEntity(position) instanceof IComputerPort port
                ? new ComputerCraftPeripheral(port.getComputerPeripheral())
                : null;
    }

    public ComputerCraftPeripheral(final MultiblockComputerPeripheral<?, ?> peripheral) {

        this._peripheral = peripheral;
        this._methods = peripheral.getMethods();
        this._names = this._methods.stream().map(ComputerMethod::getName).toArray(String[]::new);
    }

    //region IDynamicPeripheral

    @Override
    public String[] getMethodNames() {
        return this._names.clone();
    }

    @Override
    public MethodResult callMethod(final IComputerAccess computer, final ILuaContext context, final int method,
                                   final IArguments arguments) throws LuaException {

        if (method < 0 || method >= this._methods.size()) {
            throw new LuaException("No such method");
        }

        final String name = this._names[method];
        final Object[] values = arguments.getAll();

        if (this._methods.get(method).isMainThreadOnly()) {
            return context.executeMainThreadTask(() -> pack(this.invoke(name, values)));
        }

        final Object result = this.invoke(name, values);

        return null == result ? MethodResult.of() : MethodResult.of(result);
    }

    @Override
    public String getType() {
        return this._peripheral.getPeripheralStaticName();
    }

    @Override
    public Object getTarget() {
        return this._peripheral.getPart();
    }

    @Override
    public boolean equals(@Nullable final IPeripheral other) {
        return other instanceof ComputerCraftPeripheral peripheral && peripheral._peripheral == this._peripheral;
    }

    //endregion
    //region internals

    @Nullable
    private Object invoke(final String name, final Object[] arguments) throws LuaException {

        try {
            return this._peripheral.invoke(name, arguments);
        } catch (final IllegalArgumentException | IllegalStateException ex) {
            throw new LuaException(ex.getMessage());
        }
    }

    @Nullable
    private static Object[] pack(@Nullable final Object result) {
        return null == result ? null : new Object[]{result};
    }

    private final MultiblockComputerPeripheral<?, ?> _peripheral;
    private final List<? extends ComputerMethod<?>> _methods;
    private final String[] _names;

    //endregion
}
