package ports.jurassicraft.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;

/**
 * The GameTests both loaders run from one body. Each loader registers them through its own
 * generator ({@code JurassiCraftForgeGameTests}, {@code JurassiCraftFabricGameTests}), which
 * installs its {@link TestPlatform} first. A generator keeps the full {@code namespace:path}
 * templates and the vanilla {@code class.method} test names, which Forge's {@code @GameTest}
 * handling would otherwise prefix and Fabric's would read as instance methods.
 */
public final class SharedGameTests {
    private static final List<Class<?>> CLASSES = List.of(
            ports.jurassicraft.assembly.AssemblyGameTests.class,
            ports.jurassicraft.bugs.BugFeederGameTests.class,
            ports.jurassicraft.bugs.BugGameTests.class,
            ports.jurassicraft.cleaning.CleaningStationGameTests.class,
            ports.jurassicraft.cleaning.FossilMachineGameTests.class,
            ports.jurassicraft.content.MossRecipeGameTests.class,
            ports.jurassicraft.cultivator.CultivatorGameTests.class,
            ports.jurassicraft.darts.DartGameTests.class,
            ports.jurassicraft.egg.FossilNestGameTests.class,
            ports.jurassicraft.entity.DinosaurActionGameTests.class,
            ports.jurassicraft.entity.DinosaurFamilyGameTests.class,
            ports.jurassicraft.entity.DinosaurGameTests.class,
            ports.jurassicraft.entity.DinosaurInjuryGameTests.class,
            ports.jurassicraft.entity.DinosaurPoseEngineGameTests.class,
            ports.jurassicraft.entity.DinosaurPresentationGameTests.class,
            ports.jurassicraft.entity.DinosaurProceduralGameTests.class,
            ports.jurassicraft.feeder.FeederGameTests.class,
            ports.jurassicraft.fence.FenceGameTests.class,
            ports.jurassicraft.fossil.FossilLootGameTests.class,
            ports.jurassicraft.gametest.CoreFossilGameTests.class,
            ports.jurassicraft.gametest.CoreRecipeGameTests.class,
            ports.jurassicraft.gametest.GeneticsMachineGameTests.class,
            ports.jurassicraft.genetics.HybridizationGameTests.class,
            ports.jurassicraft.genetics.LabExtensionGameTests.class,
            ports.jurassicraft.goat.GoatGameTests.class,
            ports.jurassicraft.grinder.FossilGrinderGameTests.class,
            ports.jurassicraft.hatching.HatchedEggGameTests.class,
            ports.jurassicraft.hatching.HatcheryGameTests.class,
            ports.jurassicraft.loot.BonusLootGameTests.class,
            ports.jurassicraft.machine.DnaMachineGameTests.class,
            ports.jurassicraft.mural.MuralGameTests.class,
            ports.jurassicraft.music.MusicGameTests.class,
            ports.jurassicraft.plantculture.PlantCultureGameTests.class,
            ports.jurassicraft.plants.AncientTreeGameTests.class,
            ports.jurassicraft.playtest.MachineTransferGameTests.class,
            ports.jurassicraft.skull.SkullGameTests.class,
            ports.jurassicraft.structures.ParkStructureGameTests.class,
            ports.jurassicraft.structures.SpawnParkCommandGameTests.class,
            ports.jurassicraft.validation.DatapackLoadingGameTests.class,
            ports.jurassicraft.validation.RegistryResourceGameTests.class,
            ports.jurassicraft.validation.RevivalChainGameTests.class,
            ports.jurassicraft.vehicle.VehicleGameTests.class,
            ports.jurassicraft.village.PaleontologistGameTests.class,
            ports.jurassicraft.village.VillageGameTests.class,
            ports.jurassicraft.village.ZombieVillageGameTests.class);

    private SharedGameTests() {}

    @GameTestGenerator
    public static Collection<TestFunction> sharedTests() {
        List<TestFunction> functions = new ArrayList<>();
        for (Class<?> testClass : CLASSES) {
            Method[] methods = Arrays.stream(testClass.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(GameTest.class))
                    .sorted(Comparator.comparing(Method::getName)).toArray(Method[]::new);
            if (methods.length == 0) throw new IllegalStateException(testClass + " declares no GameTests");
            for (Method method : methods) {
                if (!Modifier.isStatic(method.getModifiers()))
                    throw new IllegalStateException("shared GameTest " + method + " must be static");
                functions.add(function(testClass, method));
            }
        }
        return functions;
    }

    private static TestFunction function(Class<?> testClass, Method method) {
        GameTest test = method.getAnnotation(GameTest.class);
        if (!test.template().contains(":"))
            throw new IllegalStateException("shared GameTest " + method + " needs a namespace:path template");
        method.setAccessible(true);
        String name = testClass.getSimpleName().toLowerCase(Locale.ROOT) + "." + method.getName().toLowerCase(Locale.ROOT);
        return new TestFunction(test.batch(), name, test.template(), StructureUtils.getRotationForRotationSteps(test.rotationSteps()),
                test.timeoutTicks(), test.setupTicks(), test.required(), test.requiredSuccesses(), test.attempts(),
                helper -> {
                    try {
                        method.invoke(null, helper);
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                        if (e.getCause() instanceof Error error) throw error;
                        throw new IllegalStateException(e.getCause());
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });
    }
}
