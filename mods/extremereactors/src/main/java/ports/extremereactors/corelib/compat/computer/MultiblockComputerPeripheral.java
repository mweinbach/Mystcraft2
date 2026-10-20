/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.computer;

import com.google.common.collect.ImmutableList;
import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The computer interface of a multiblock machine, reached through one of its parts.
 *
 * <p>This class does not depend on any computer mod: a loader adapter exposes {@link #getMethods()} to the
 * computer mod present at runtime. Every machine peripheral offers {@code getConnected},
 * {@code getMultiblockAssembled}, {@code getMinimumCoordinate} and {@code getMaximumCoordinate}.</p>
 */
public abstract class MultiblockComputerPeripheral<Controller extends AbstractCuboidMultiblockController<Controller>,
                                                   Part extends AbstractMultiblockEntity<Controller>> {

    protected MultiblockComputerPeripheral(final Part part) {

        this._part = part;
        this._methods = null;
    }

    public Part getPart() {
        return this._part;
    }

    /**
     * @return the peripheral type name reported to the computers
     */
    public abstract String getPeripheralStaticName();

    /**
     * Collect the methods of the peripheral. Overrides must call this implementation first.
     */
    public void populateMethods(final Consumer<ComputerMethod<MultiblockComputerPeripheral<Controller, Part>>> methodConsumer) {

        methodConsumer.accept(new ComputerMethod<>("getConnected", (peripheral, arguments) -> peripheral.isAssembled()));
        methodConsumer.accept(new ComputerMethod<>("getMultiblockAssembled", (peripheral, arguments) -> peripheral.isAssembled()));
        methodConsumer.accept(new ComputerMethod<>("getMinimumCoordinate", wrapControllerValue(c -> c.getBoundingBox().getMin())));
        methodConsumer.accept(new ComputerMethod<>("getMaximumCoordinate", wrapControllerValue(c -> c.getBoundingBox().getMax())));
    }

    /**
     * @return the methods of the peripheral, in the order they were added
     */
    public List<ComputerMethod<MultiblockComputerPeripheral<Controller, Part>>> getMethods() {
        return ImmutableList.copyOf(this.methods().values());
    }

    @Nullable
    public ComputerMethod<MultiblockComputerPeripheral<Controller, Part>> getMethod(final String name) {
        return this.methods().get(name);
    }

    /**
     * Run a method by name.
     *
     * @throws IllegalArgumentException if the method does not exist or its arguments are not valid
     * @throws IllegalStateException if the machine is not assembled
     */
    public Object invoke(final String name, final Object[] arguments) {

        final ComputerMethod<MultiblockComputerPeripheral<Controller, Part>> method = this.getMethod(name);

        if (null == method) {
            throw new IllegalArgumentException("No such method: " + name);
        }

        return method.invoke(this, arguments);
    }

    //region method wrappers

    protected BiFunction<MultiblockComputerPeripheral<Controller, Part>, Object[], Object> wrapControllerValue(
            final Function<Controller, Object> code) {
        return (peripheral, arguments) -> code.apply(peripheral.getAssembledController());
    }

    protected BiFunction<MultiblockComputerPeripheral<Controller, Part>, Object[], Object> wrapControllerValue(
            final BiFunction<Controller, Object[], Object> code) {
        return (peripheral, arguments) -> code.apply(peripheral.getAssembledController(), arguments);
    }

    protected BiFunction<MultiblockComputerPeripheral<Controller, Part>, Object[], Object> wrapControllerAction(
            final Consumer<Controller> code) {
        return (peripheral, arguments) -> {

            code.accept(peripheral.getAssembledController());
            return null;
        };
    }

    protected BiFunction<MultiblockComputerPeripheral<Controller, Part>, Object[], Object> wrapControllerAction(
            final BiConsumer<Controller, Object[]> code) {
        return (peripheral, arguments) -> {

            code.accept(peripheral.getAssembledController(), arguments);
            return null;
        };
    }

    //endregion
    //region internals

    private boolean isAssembled() {
        return this._part.getMultiblockController()
                .filter(AbstractCuboidMultiblockController::isAssembled)
                .isPresent();
    }

    private Controller getAssembledController() {
        return this._part.getMultiblockController()
                .filter(AbstractCuboidMultiblockController::isAssembled)
                .orElseThrow(() -> new IllegalStateException("The machine is not assembled"));
    }

    private Map<String, ComputerMethod<MultiblockComputerPeripheral<Controller, Part>>> methods() {

        if (null == this._methods) {

            final Map<String, ComputerMethod<MultiblockComputerPeripheral<Controller, Part>>> methods = new LinkedHashMap<>();

            this.populateMethods(method -> methods.put(method.getName(), method));
            this._methods = methods;
        }

        return this._methods;
    }

    private final Part _part;
    @Nullable
    private Map<String, ComputerMethod<MultiblockComputerPeripheral<Controller, Part>>> _methods;

    //endregion
}
