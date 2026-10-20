/*
 *
 * FluidizerPowerPortEntity.java
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

package it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.part;

import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.fluidizer.MultiblockFluidizer;
import ports.extremereactors.corelib.energy.NullEnergyHandlers;
import ports.extremereactors.corelib.energy.adapter.ForgeEnergyAdapter;
import ports.extremereactors.corelib.energy.handler.WideEnergyStorageForwarder2;
import ports.extremereactors.multiblock.cuboid.PartPosition;
import ports.extremereactors.multiblock.validation.IMultiblockValidator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityManager;
import ports.extremereactors.corelib.capability.CapabilityToken;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FluidizerPowerPortEntity
        extends AbstractFluidizerEntity {

    public FluidizerPowerPortEntity(final BlockPos position, final BlockState blockState) {

        super(Content.TileEntityTypes.FLUIDIZER_POWERPORT.get(), position, blockState);
        this._forwarder = new WideEnergyStorageForwarder2(NullEnergyHandlers.WIDE_STORAGE);
        this._capability = LazyOptional.of(() -> ForgeEnergyAdapter.wrap(this._forwarder));
    }

    //region AbstractCuboidMultiblockPart

    @Override
    public void onPostMachineAssembled(final MultiblockFluidizer controller) {

        super.onPostMachineAssembled(controller);
        this._forwarder.setHandler(this.getEnergyStorage());
    }

    @Override
    public void onPostMachineBroken() {

        super.onPostMachineBroken();
        this._forwarder.setHandler(NullEnergyHandlers.WIDE_STORAGE);
    }

    @Override
    public boolean isGoodForPosition(final PartPosition position, final IMultiblockValidator validatorCallback) {
        return position.isFace() || super.isGoodForPosition(position, validatorCallback);
    }

    //endregion
    //region TileEntity

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return CAPAP_FORGE_ENERGYSTORAGE == cap ? this._capability.cast() : super.getCapability(cap, side);
    }

    //endregion
    //region internals

    @SuppressWarnings("FieldMayBeFinal")
    private static Capability<IEnergyStorage> CAPAP_FORGE_ENERGYSTORAGE = CapabilityManager.get(new CapabilityToken<>(){});

    private final WideEnergyStorageForwarder2 _forwarder;
    private final LazyOptional<IEnergyStorage> _capability;

    //endregion
}
