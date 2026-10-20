/*
 *
 * ConfigRecipeCondition.java
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

package it.zerono.mods.extremereactors.config.conditions;

import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.config.Config;
import net.minecraft.resources.ResourceLocation;

/**
 * The {@code bigreactors:config_condition} recipe condition: a recipe is loaded only if the named config option is on.
 * JSON: {@code {"type": "bigreactors:config_condition", "config_name": "..."}}. The loader adapters register it with
 * their own recipe condition systems.
 */
public final class ConfigCondition {

    public static final ResourceLocation ID = ExtremeReactors.newID("config_condition");
    public static final String JSON_CONFIG_NAME = "config_name";

    public static boolean test(final String configName) {

        switch (configName) {

            default:
                return false;

            // recipes

            case "registerCharcoalForSmelting":
                return Config.COMMON.recipes.registerCharcoalForSmelting.get();

            case "registerCoalForSmelting":
                return Config.COMMON.recipes.registerCoalForSmelting.get();

            case "registerCoalBlockForSmelting":
                return Config.COMMON.recipes.registerCoalBlockForSmelting.get();
        }
    }

    private ConfigCondition() {
    }
}
