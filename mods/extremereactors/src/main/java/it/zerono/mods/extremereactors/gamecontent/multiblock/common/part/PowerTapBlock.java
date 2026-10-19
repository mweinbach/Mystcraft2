/*
 *
 * PowerTapBlock.java
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

package it.zerono.mods.extremereactors.gamecontent.multiblock.common.part;

import ports.extremereactors.corelib.base.multiblock.part.GenericDeviceBlock;
import ports.extremereactors.corelib.block.INeighborChangeListener;
import ports.extremereactors.corelib.block.multiblock.IMultiblockPartType;
import ports.extremereactors.multiblock.IMultiblockController;

import ports.extremereactors.corelib.block.multiblock.MultiblockPartBlock.MultiblockPartProperties;

public class PowerTapBlock<Controller extends IMultiblockController<Controller>,
            PartType extends Enum<PartType> & IMultiblockPartType>
        extends GenericDeviceBlock<Controller, PartType>
        implements INeighborChangeListener.Notifier {

    public PowerTapBlock(final MultiblockPartProperties<PartType> properties) {
        super(properties);
    }
}
