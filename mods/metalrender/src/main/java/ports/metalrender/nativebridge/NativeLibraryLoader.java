/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.nativebridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Extracts the universal dylib and the Metal shader libraries bundled under {@code /natives/macos}
 * and loads the dylib. Files are written to a temporary name and atomically moved into place so a
 * second game instance never sees a half-written (or rewritten, still-mapped) library.
 */
public final class NativeLibraryLoader {

    public static final String RESOURCE_DIR = "/natives/macos/";
    public static final String LIBRARY = "libmetalrender.dylib";
    public static final String[] SHADER_LIBRARIES = {
        "shaders_metal3.metallib", "shaders_metal4.metallib", "shaders_metal4_1.metallib"
    };

    private static Path loadedFrom;

    private NativeLibraryLoader() {
    }

    public static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    }

    /**
     * Extracts the natives into {@code directory}, loads the dylib once per JVM and returns the
     * directory holding the metallibs (pass it to {@link MetalNative#initContext}).
     */
    public static synchronized Path load(final Path directory) throws IOException {
        if (loadedFrom != null) {
            return loadedFrom;
        }
        if (!isMacOs()) {
            throw new IOException("MetalRender natives are macOS-only (os.name=" + System.getProperty("os.name") + ")");
        }
        Files.createDirectories(directory);
        extract(LIBRARY, directory, true);
        for (final String shaderLibrary : SHADER_LIBRARIES) {
            extract(shaderLibrary, directory, false);
        }
        try {
            System.load(directory.resolve(LIBRARY).toAbsolutePath().toString());
        } catch (final UnsatisfiedLinkError e) {
            throw new IOException("could not load " + LIBRARY + ": " + e.getMessage(), e);
        }
        loadedFrom = directory;
        return directory;
    }

    public static synchronized boolean isLoaded() {
        return loadedFrom != null;
    }

    private static void extract(final String name, final Path directory, final boolean required) throws IOException {
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (in == null) {
                if (required) {
                    throw new IOException("missing bundled native " + RESOURCE_DIR + name);
                }
                return;
            }
            final Path target = directory.resolve(name);
            final Path temp = Files.createTempFile(directory, name, ".tmp");
            try {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }
}
