/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.forge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import mekanism.common.Mekanism;
import mekanism.gametest.AdditionsModuleGameTests;
import mekanism.gametest.ChemicalMachineGameTests;
import mekanism.gametest.ChunkLoadingGameTests;
import mekanism.gametest.CoreMachineGameTests;
import mekanism.gametest.DynamicTankGameTests;
import mekanism.gametest.FactoryGameTests;
import mekanism.gametest.FissionReactorGameTests;
import mekanism.gametest.FluidGameTests;
import mekanism.gametest.GeneratorsModuleGameTests;
import mekanism.gametest.InductionMatrixGameTests;
import mekanism.gametest.IndustrialTurbineGameTests;
import mekanism.gametest.ItemBlockHookGameTests;
import mekanism.gametest.LoaderInteropGameTests;
import mekanism.gametest.OreGenerationGameTests;
import mekanism.gametest.PigmentMachineGameTests;
import mekanism.gametest.PlayerEventGameTests;
import mekanism.gametest.RenderBoundsGameTests;
import mekanism.gametest.SimpleGeneratorGameTests;
import mekanism.gametest.ThermalEvaporationGameTests;
import mekanism.gametest.ThermoelectricBoilerGameTests;
import mekanism.gametest.ToolsModuleGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Registers the shared GameTests on Forge. A generator keeps their full {@code namespace:path} templates and Fabric's
 * {@code class.method} names, which Forge's {@code @GameTest} handling would prefix again.
 */
@GameTestHolder(Mekanism.MODID)
public class ForgeGameTests {

    private static final List<Class<?>> SHARED_TESTS = List.of(AdditionsModuleGameTests.class, ChemicalMachineGameTests.class,
          ChunkLoadingGameTests.class, CoreMachineGameTests.class, DynamicTankGameTests.class, FactoryGameTests.class,
          FissionReactorGameTests.class, FluidGameTests.class, GeneratorsModuleGameTests.class, InductionMatrixGameTests.class,
          IndustrialTurbineGameTests.class, ItemBlockHookGameTests.class, LoaderInteropGameTests.class,
          OreGenerationGameTests.class, PigmentMachineGameTests.class, PlayerEventGameTests.class, RenderBoundsGameTests.class,
          SimpleGeneratorGameTests.class, ThermalEvaporationGameTests.class, ThermoelectricBoilerGameTests.class,
          ToolsModuleGameTests.class);

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
