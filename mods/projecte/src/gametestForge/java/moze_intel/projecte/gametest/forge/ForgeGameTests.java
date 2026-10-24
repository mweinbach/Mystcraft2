/*
 * ProjectE 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest.forge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import moze_intel.projecte.PECore;
import moze_intel.projecte.gametest.EmcBlockGameTests;
import moze_intel.projecte.gametest.EmcMapperGameTests;
import moze_intel.projecte.gametest.MatterGameTests;
import moze_intel.projecte.gametest.PhilosophersStoneGameTests;
import moze_intel.projecte.gametest.TransmutationGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Registers the shared GameTests on Forge. A generator keeps their full {@code namespace:path} templates and Fabric's
 * {@code class.method} names, which Forge's {@code @GameTest} handling would prefix again.
 */
@GameTestHolder(PECore.MODID)
public class ForgeGameTests {

    private static final List<Class<?>> SHARED_TESTS = List.of(EmcMapperGameTests.class, TransmutationGameTests.class,
          EmcBlockGameTests.class, PhilosophersStoneGameTests.class, MatterGameTests.class);

    @GameTestGenerator
    public static List<TestFunction> sharedTests() {
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
                      if (e.getCause() instanceof RuntimeException runtime) {
                          throw runtime;
                      }
                      throw new RuntimeException(e.getCause());
                  } catch (IllegalAccessException e) {
                      throw new IllegalStateException(e);
                  }
              });
    }
}
