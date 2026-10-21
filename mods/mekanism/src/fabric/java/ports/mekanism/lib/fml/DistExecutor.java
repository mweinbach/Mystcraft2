/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fml;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import ports.mekanism.lib.api.distmarker.Dist;
import ports.mekanism.platform.Platform;

/**
 * Runs code only on one physical side. The nested suppliers keep side-only classes from loading on the other side.
 */
public final class DistExecutor {

    private DistExecutor() {
    }

    public static <T> T unsafeCallWhenOn(Dist dist, Supplier<Callable<T>> toRun) {
        if (Platform.dist() == dist) {
            try {
                return toRun.get().call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static <T> T safeCallWhenOn(Dist dist, Supplier<SafeCallable<T>> toRun) {
        if (Platform.dist() == dist) {
            try {
                return toRun.get().call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {
        if (Platform.dist() == dist) {
            toRun.get().run();
        }
    }

    public static void safeRunWhenOn(Dist dist, Supplier<SafeRunnable> toRun) {
        if (Platform.dist() == dist) {
            toRun.get().run();
        }
    }

    public static <T> T unsafeRunForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) {
        return Platform.dist() == Dist.CLIENT ? clientTarget.get().get() : serverTarget.get().get();
    }

    public static <T> T safeRunForDist(Supplier<SafeSupplier<T>> clientTarget, Supplier<SafeSupplier<T>> serverTarget) {
        return Platform.dist() == Dist.CLIENT ? clientTarget.get().get() : serverTarget.get().get();
    }

    public interface SafeCallable<T> extends Callable<T> {
    }

    public interface SafeSupplier<T> extends Supplier<T> {
    }

    public interface SafeRunnable extends Runnable {
    }
}
