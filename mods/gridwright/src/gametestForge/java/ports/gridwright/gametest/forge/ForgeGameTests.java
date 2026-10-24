package ports.gridwright.gametest.forge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;
import ports.gridwright.Gridwright;
import ports.gridwright.gametest.EmcGameTests;
import ports.gridwright.gametest.GeneratorGameTests;
import ports.gridwright.gametest.GridGameTests;
import ports.gridwright.gametest.ProducerGameTests;
import ports.gridwright.gametest.ResonatorGameTests;
import ports.gridwright.gametest.TestPlatform;
import ports.gridwright.gametest.UtilityBlockGameTests;
import ports.gridwright.gametest.UtilityItemGameTests;
import ports.gridwright.gametest.WardGameTests;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Registers the shared GameTests on Forge. A generator keeps their full {@code namespace:path}
 * templates and Fabric's {@code class.method} names, which Forge's {@code @GameTest} handling
 * would prefix again.
 */
@GameTestHolder(Gridwright.MOD_ID)
public class ForgeGameTests {

    private static final List<Class<?>> SHARED_TESTS = List.of(GridGameTests.class, ProducerGameTests.class,
            GeneratorGameTests.class, ResonatorGameTests.class, EmcGameTests.class,
            UtilityItemGameTests.class, UtilityBlockGameTests.class,
            WardGameTests.class);

    @GameTestGenerator
    public static List<TestFunction> sharedTests() {
        TestPlatform.Holder.install(new ForgeTestPlatform());
        ForgeTestPlatform.registerEnergySink();
        ForgeTestPlatform.registerClaims();
        List<TestFunction> functions = new ArrayList<>();
        for (Class<?> testClass : SHARED_TESTS) {
            Arrays.stream(testClass.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(GameTest.class) && Modifier.isStatic(method.getModifiers()))
                    .sorted(Comparator.comparing(Method::getName))
                    .forEach(method -> functions.add(function(testClass, method)));
        }
        return functions;
    }

    private static TestFunction function(Class<?> testClass, Method method) {
        GameTest test = method.getAnnotation(GameTest.class);
        String name = testClass.getSimpleName().toLowerCase(Locale.ROOT) + "." + method.getName().toLowerCase(Locale.ROOT);
        return new TestFunction(test.batch(), name, test.template(), StructureUtils.getRotationForRotationSteps(test.rotationSteps()),
                test.timeoutTicks(), test.setupTicks(), test.required(), test.requiredSuccesses(), test.attempts(),
                helper -> {
                    try {
                        method.invoke(null, helper);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                        throw new RuntimeException(e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }
}
