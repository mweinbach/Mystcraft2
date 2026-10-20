/*
 *
 * ExtremeReactors.java
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

package it.zerono.mods.extremereactors;

import it.zerono.mods.extremereactors.api.internal.InternalDispatcher;
import it.zerono.mods.extremereactors.api.internal.modpack.wrapper.ApiWrapper;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.gamecontent.Content;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.ReactantHelper;
import it.zerono.mods.extremereactors.gamecontent.command.ExtremeReactorsCommand;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.network.UpdateClientsFuelRodsLayout;
import it.zerono.mods.extremereactors.proxy.IProxy;
import it.zerono.mods.extremereactors.proxy.ServerProxy;
import ports.extremereactors.corelib.internal.Lib;
import ports.extremereactors.corelib.network.IModMessage;
import ports.extremereactors.corelib.network.NetworkHandler;
import ports.extremereactors.platform.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.function.Function;
import java.util.stream.Stream;

public class ExtremeReactors {

    public static final String MOD_ID = "bigreactors";
    public static final String MOD_NAME = "Extreme Reactors 2";

    /** IMC methods processed by {@link #onInterModProcess(Function)}, in processing order. */
    public static final String[] IMC_METHODS = {
            // - Reactor reactants/mapping/reactions
            "reactant-register", "mapping-register", "reaction-register",
            "reaction-remove", "mapping-remove", "reactant-remove",
            // - Reactor Moderators
            "moderator-s-register", "moderator-f-register", "moderator-s-remove", "moderator-f-remove",
            // - Coolants / Vapors
            "fluid-register", "fluid-mapping-register", "fluid-transition-register",
            "fluid-transition-remove", "fluid-mapping-remove", "fluid-remove",
            // - Turbine CoilMaterials
            "coilmaterial-register", "coilmaterial-remove",
    };

    public static ExtremeReactors getInstance() {
        return s_instance;
    }

    public static IProxy getProxy() {
        return s_proxy;
    }

    /** Called by the client entrypoint to install the client proxy. */
    public static void setProxy(final IProxy proxy) {
        s_proxy = proxy;
    }

    public static ResourceLocation newID(final String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    /**
     * Create the mod. The loader entrypoint calls this once, after installing the platform backend.
     */
    public static ExtremeReactors initialize() {

        if (null == s_instance) {
            s_instance = new ExtremeReactors();
        }

        return s_instance;
    }

    /**
     * Called on both the physical client and the physical server to perform common initialization tasks
     */
    public void onCommonInit() {
        this._network.registerMessage(UpdateClientsFuelRodsLayout.class, UpdateClientsFuelRodsLayout::new);
    }

    /**
     * Process the API messages other mods sent and the modpack API file.
     *
     * @param imcMessages the messages sent for an IMC method; loaders without inter-mod messages return an empty stream
     */
    public void onInterModProcess(final Function<String, Stream<Runnable>> imcMessages) {

        for (final String method : IMC_METHODS) {
            Stream.concat(imcMessages.apply(method), InternalDispatcher.drain(method)).forEach(Runnable::run);
        }

        InternalDispatcher.open();

        // ModPack API Wrapper
        ApiWrapper.processFile();
    }

    public <T extends IModMessage> void sendPacket(final T packet, final Level world, final BlockPos center, final int radius) {
        this._network.sendToAllAround(packet, center.getX(), center.getY(), center.getZ(), radius, world.dimension());
    }

    //region internals

    private ExtremeReactors() {

        Lib.initialize();
        this._network = new NetworkHandler(newID("network"), "1");

        Config.initialize();
        Content.initialize();

        Platform.backend().onRegisterCommands(ExtremeReactorsCommand::register);
        Platform.backend().onTagsUpdated(ReactantHelper::onVanillaTagsUpdated);
    }

    private static ExtremeReactors s_instance;
    private static IProxy s_proxy = new ServerProxy();
    private final NetworkHandler _network;

    //endregion
}
