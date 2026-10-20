/*
 *
 * ReprocessorAccessPortEntity.java
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

package it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.part;

import it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.MultiblockReprocessor;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reprocessor.container.ReprocessorAccessPortContainer;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.item.ItemHelper;
import ports.extremereactors.corelib.item.inventory.handler.ItemHandlerModifiableForwarder;
import ports.extremereactors.multiblock.cuboid.PartPosition;
import ports.extremereactors.multiblock.validation.IMultiblockValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityManager;
import ports.extremereactors.corelib.capability.CapabilityToken;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ReprocessorAccessPortEntity
        extends AbstractReprocessorEntity
        implements MenuProvider {

    public ReprocessorAccessPortEntity(final BlockEntityType<?> type, final IoDirection direction,
                                       final BlockPos position, final BlockState blockState) {

        super(type, position, blockState);
        this._direction = direction;
        this._forwarder = new ItemHandlerModifiableForwarder(ItemHelper.EMPTY_ITEM_HANDLER);
        this._capability = LazyOptional.of(() -> this._forwarder);
    }

    public IItemHandler getHandler() {
        return this._forwarder;
    }

    public IoDirection getDirection() {
        return this._direction;
    }

    //region MenuProvider

    /**
     * Create the SERVER-side container for this TileEntity
     * @param windowId  the window id
     * @param inventory the player inventory
     * @param player    the player
     * @return the container to use on the server
     */
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(final int windowId, final Inventory inventory, final Player player) {
        return new ReprocessorAccessPortContainer(windowId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return super.getPartDisplayName();
    }

    //endregion
    //region AbstractModBlockEntity

    /**
     * Check if the tile entity has a GUI or not
     * Override in derived classes to return true if your tile entity got a GUI
     */
    @Override
    public boolean canOpenGui(Level world, BlockPos position, BlockState state) {
        return this.isMachineAssembled();
    }

    //endregion
    //region AbstractCuboidMultiblockPart

    @Override
    public void onPostMachineAssembled(MultiblockReprocessor controller) {

        super.onPostMachineAssembled(controller);
        this._forwarder.setHandler(this.getItemInventory(this._direction));
    }

    @Override
    public void onPostMachineBroken() {

        super.onPostMachineBroken();
        this._forwarder.setHandler(ItemHelper.EMPTY_ITEM_HANDLER);
    }

    @Override
    public boolean isGoodForPosition(final PartPosition position, final IMultiblockValidator validatorCallback) {
        return (this._direction.isInput() && PartPosition.TopFace == position) ||
                (this._direction.isOutput() && position.isFace()) || super.isGoodForPosition(position, validatorCallback);
    }

    //endregion
    //region TileEntity

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return ITEM_HANDLER_CAPABILITY == cap ? this._capability.cast() : super.getCapability(cap, side);
    }

    //endregion
    //region internals

    @SuppressWarnings("FieldMayBeFinal")
    private static Capability<IItemHandler> ITEM_HANDLER_CAPABILITY = CapabilityManager.get(new CapabilityToken<>(){});

    private final IoDirection _direction;
    private final ItemHandlerModifiableForwarder _forwarder;
    private final LazyOptional<IItemHandlerModifiable> _capability;

    //endregion
}
