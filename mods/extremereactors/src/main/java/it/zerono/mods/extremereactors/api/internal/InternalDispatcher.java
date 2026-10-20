/*
 *
 * InternalDispatcher.java
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

package it.zerono.mods.extremereactors.api.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class InternalDispatcher {

    public static void dispatch(final String method, final Runnable action) {

        Preconditions.checkArgument(!Strings.isNullOrEmpty(method));
        Preconditions.checkNotNull(action);

        synchronized (QUEUED) {

            if (!s_open && 0 == s_internalDepth) {

                QUEUED.computeIfAbsent(method, k -> new ArrayList<>()).add(action);
                return;
            }
        }

        action.run();
    }

    /**
     * Run the registrations of Extreme Reactors itself: they are applied immediately, while the calls of other
     * mods wait for {@link #drain(String)}.
     */
    public static void runInternal(final Runnable registrations) {

        synchronized (QUEUED) {
            ++s_internalDepth;
        }

        try {
            registrations.run();
        } finally {
            synchronized (QUEUED) {
                --s_internalDepth;
            }
        }
    }

    /**
     * @return the calls other mods made for an API method so far, in call order
     */
    public static Stream<Runnable> drain(final String method) {

        synchronized (QUEUED) {

            final List<Runnable> actions = QUEUED.remove(method);

            return null == actions ? Stream.empty() : actions.stream();
        }
    }

    /**
     * From now on, run every call immediately.
     */
    public static void open() {

        synchronized (QUEUED) {

            s_open = true;
            QUEUED.clear();
        }
    }

    private static final Map<String, List<Runnable>> QUEUED = new HashMap<>();
    private static boolean s_open;
    private static int s_internalDepth;

    private InternalDispatcher() {
    }
}
