/*
 *
 * Config.java
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

package it.zerono.mods.extremereactors.config;

import ports.extremereactors.corelib.CodeHelper;
import ports.extremereactors.corelib.config.ConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import ports.extremereactors.platform.Platform;

public final class Config {

    public static final Client CLIENT;
    public static final Common COMMON;

    public static void initialize() {

        register("client", Config.s_clientSpec);
        register("common", Config.s_commonSpec);
    }

    //region internals

    private static final ConfigSpec s_clientSpec;
    private static final ConfigSpec s_commonSpec;

    static {

        if (!CodeHelper.ioCreateModConfigDirectory("extremereactors")) {
            throw new RuntimeException("Unable to create a directory for the Extreme Reactors config files");
        }

        final Pair<Client, ConfigSpec> pair1 = new ConfigSpec.Builder().configure(Client::new);
        final Pair<Common, ConfigSpec> pair2 = new ConfigSpec.Builder().configure(Common::new);

        CLIENT = pair1.getLeft();
        s_clientSpec = pair1.getRight();

        COMMON = pair2.getLeft();
        s_commonSpec = pair2.getRight();
    }

    private static void register(final String type, final ConfigSpec spec) {
        spec.load(Platform.configDirectory().resolve(String.format("extremereactors/%s.toml", type)));
    }

    //endregion
}
