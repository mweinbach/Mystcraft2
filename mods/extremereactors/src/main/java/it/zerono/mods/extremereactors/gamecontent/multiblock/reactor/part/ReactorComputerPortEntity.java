/*
 *
 * ReactorComputerPortEntity.java
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

package it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part;

import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.computer.ReactorComputerPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.compat.computer.IComputerPort;

public class ReactorComputerPortEntity
        extends AbstractReactorEntity
        implements IComputerPort {

    public ReactorComputerPortEntity(final BlockPos position, final BlockState blockState) {
        super(Content.TileEntityTypes.REACTOR_COMPUTERPORT.get(), position, blockState);
    }

    //region IComputerPort

    @Override
    public ReactorComputerPeripheral getComputerPeripheral() {

        if (null == this._peripheral) {
            this._peripheral = new ReactorComputerPeripheral(this);
        }

        return this._peripheral;
    }

    //endregion
    //region client render support

    @Override
    protected int getUpdatedModelVariantIndex() {
        return this.isMachineAssembled() ? 1 : 0;
    }

    //endregion
    //region internals

    private ReactorComputerPeripheral _peripheral;

    //endregion
}
