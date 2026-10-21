/*
 *
 * FluidPortHandlerMekanism.java
 *
 * This file is part of Extreme Reactors 2 by ZeroNoRyouki, a Minecraft mod.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 * DO NOT REMOVE OR EDIT THIS HEADER
 *
 */

package it.zerono.mods.extremereactors.gamecontent.multiblock.common.part.fluidport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.base.multiblock.part.io.fluid.AbstractFluidPortHandler;
import ports.extremereactors.corelib.base.multiblock.part.io.fluid.IFluidPort;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.compat.mekanism.MekanismGas;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.data.IoMode;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.EmptyFluidHandler;
import ports.extremereactors.corelib.fluid.handler.FluidHandlerForwarder;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * Offers the machine's output tank to Mekanism as a gas handler. The gas side of upstream's handler (the
 * {@code IGasHandler} methods and the rotary recipe mappings) is {@link ports.extremereactors.corelib.compat.mekanism.MekanismRotary},
 * which both loader adapters bridge to Mekanism.
 */
public class FluidPortHandlerMekanism<Controller extends AbstractCuboidMultiblockController<Controller>,
            T extends AbstractMultiblockEntity<Controller> & IFluidPort>
        extends AbstractFluidPortHandler<Controller, T> {

    public FluidPortHandlerMekanism(final T part, final IoMode mode) {

        super(part, IoMode.Passive);
        this._capabilityForwarder = new FluidHandlerForwarder(EmptyFluidHandler.INSTANCE);
        this._capability = null;
        this._consumer = LazyOptional.empty();
    }

    //region IFluidPortHandler

    /**
     * Send fluid to the connected consumer (if there is one)
     *
     * @param stack FluidStack representing the Fluid and maximum amount of fluid to be sent out.
     * @return the amount of fluid accepted by the consumer
     */
    @Override
    public int outputFluid(final FluidStack stack) {

        final IFluidHandler consumer = this._consumer.orElse(null);

        if (null == consumer || this.isPassive()) {
            return 0;
        }

        return consumer.fill(stack, IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * If this is an Active Fluid Port in input mode, try to get fluids from the connected consumer (if there is one)
     *
     * @param destination the destination IFluidHandler that will receive the fluid
     * @param maxAmount   the maximum amount of fluid to acquire
     */
    @Override
    public int inputFluid(IFluidHandler destination, int maxAmount) {
        return 0;
    }

    @Override
    public void update(final Function<IoDirection, IFluidHandler> handlerProvider) {

        final T part = this.getPart();

        this._capabilityForwarder.setHandler(handlerProvider.apply(IoDirection.Output));
        this.checkConnections(part.getLevel(), part.getWorldPosition());
    }

    //endregion
    //region IIOPortHandler

    /**
     * @return true if this handler is connected to one of it's allowed consumers, false otherwise
     */
    @Override
    public boolean isConnected() {
        return this._consumer.isPresent();
    }

    /**
     * Check for connections
     *
     * @param world    the handler world
     * @param position the handler position
     */
    @Override
    public void checkConnections(@Nullable Level world, BlockPos position) {
        this._consumer = this.lookupNeighbor(world, position, MekanismGas.HANDLER);
    }

    @Override
    public void invalidate() {

        this._consumer = LazyOptional.empty();

        if (null != this._capability) {

            this._capability.invalidate();
            this._capability = null;
        }
    }

    /**
     * Get the requested capability, if supported
     *
     * @param capability the capability
     * @param direction  the direction the request is coming from
     * @return the capability (if supported) or null (if not)
     */
    @Nullable
    @Override
    public <C> LazyOptional<C> getCapability(final Capability<C> capability, @Nullable final Direction direction) {

        if (MekanismGas.HANDLER == capability) {

            if (null == this._capability) {
                this._capability = LazyOptional.of(() -> this._capabilityForwarder);
            }

            return this._capability.cast();
        }

        return null;
    }

    //endregion
    //region internals

    private LazyOptional<IFluidHandler> _consumer;
    private final FluidHandlerForwarder _capabilityForwarder;
    @Nullable
    private LazyOptional<IFluidHandler> _capability;

    //endregion
}
