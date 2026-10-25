/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;

/**
 * Finds every shared GameTest class in this package. A new test class is picked up on both loaders; nothing else
 * lists it. The generator keeps each method's {@code namespace:path} template and Fabric's {@code class.method} name.
 */
public class SharedGameTests {

    private static final String PACKAGE = "mekanism.gametest";

    @GameTestGenerator
    public static List<TestFunction> sharedTests() {
        List<TestFunction> functions = new ArrayList<>();
        for (Class<?> testClass : testClasses()) {
            Arrays.stream(testClass.getDeclaredMethods())
                  .filter(method -> method.isAnnotationPresent(GameTest.class) && Modifier.isStatic(method.getModifiers()))
                  .sorted(Comparator.comparing(Method::getName))
                  .forEach(method -> functions.add(function(testClass, method)));
        }
        if (functions.isEmpty()) {
            throw new IllegalStateException("No shared GameTests were found in " + PACKAGE);
        }
        return functions;
    }

    private static List<Class<?>> testClasses() {
        ClassLoader loader = SharedGameTests.class.getClassLoader();
        List<Class<?>> classes = new ArrayList<>();
        for (String simpleName : classFileNames()) {
            Class<?> testClass;
            try {
                testClass = Class.forName(PACKAGE + "." + simpleName, false, loader);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("GameTest class file did not load: " + simpleName, e);
            }
            boolean hasTest = Arrays.stream(testClass.getDeclaredMethods())
                  .anyMatch(method -> method.isAnnotationPresent(GameTest.class) && Modifier.isStatic(method.getModifiers()));
            if (hasTest) {
                classes.add(testClass);
            }
        }
        classes.sort(Comparator.comparing(Class::getName));
        return classes;
    }

    private static List<String> classFileNames() {
        URL url = SharedGameTests.class.getResource("SharedGameTests.class");
        if (url == null) {
            throw new IllegalStateException("Could not locate SharedGameTests.class to scan " + PACKAGE);
        }
        try {
            List<String> names = "jar".equals(url.getProtocol()) ? namesInJar(url) : namesInDirectory(url);
            if (names.isEmpty()) {
                throw new IOException("No class files next to " + url);
            }
            names.sort(Comparator.naturalOrder());
            return names;
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Could not scan GameTests from " + url, e);
        }
    }

    private static List<String> namesInDirectory(URL url) throws IOException, URISyntaxException {
        // Forge userdev serves mod classes through a union filesystem. Paths.get reads that, and ordinary
        // file URLs, without assuming the scheme is file.
        Path classFile = Paths.get(url.toURI());
        Path directory = Files.isDirectory(classFile) ? classFile : classFile.getParent();
        List<String> names = new ArrayList<>();
        try (Stream<Path> children = Files.list(directory)) {
            for (Path child : children.toList()) {
                String name = simpleClassName(child.getFileName().toString());
                if (name != null) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static List<String> namesInJar(URL url) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        String entryName = connection.getEntryName();
        int slash = entryName.lastIndexOf('/');
        String prefix = slash < 0 ? "" : entryName.substring(0, slash + 1);
        try (JarFile jar = connection.getJarFile()) {
            return namesInJarPrefix(jar, prefix);
        }
    }

    private static List<String> namesInJarPrefix(JarFile jar, String prefix) {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!name.startsWith(prefix)) {
                continue;
            }
            String rest = name.substring(prefix.length());
            if (rest.indexOf('/') >= 0) {
                continue;
            }
            String simpleName = simpleClassName(rest);
            if (simpleName != null) {
                names.add(simpleName);
            }
        }
        return new ArrayList<>(names);
    }

    private static String simpleClassName(String fileName) {
        if (!fileName.endsWith(".class") || fileName.contains("$")) {
            return null;
        }
        String simpleName = fileName.substring(0, fileName.length() - ".class".length());
        if ("package-info".equals(simpleName) || "module-info".equals(simpleName)) {
            return null;
        }
        return simpleName;
    }

    private static TestFunction function(Class<?> testClass, Method method) {
        GameTest test = method.getAnnotation(GameTest.class);
        String name = testClass.getSimpleName().toLowerCase(Locale.ROOT) + "." + method.getName().toLowerCase(Locale.ROOT);
        boolean levelTickingOnly = testClass.isAnnotationPresent(LevelTickingOnly.class) || method.isAnnotationPresent(LevelTickingOnly.class);
        return new TestFunction(test.batch(), name, test.template(), StructureUtils.getRotationForRotationSteps(test.rotationSteps()),
              test.timeoutTicks(), test.setupTicks(), test.required(), test.requiredSuccesses(), test.attempts(),
              helper -> {
                  if (!levelTickingOnly) {
                      GameTestTicking.keepTestAreaTicking(helper);
                  }
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
