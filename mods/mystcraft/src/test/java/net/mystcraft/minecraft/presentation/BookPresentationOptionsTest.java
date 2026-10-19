package net.mystcraft.minecraft.presentation;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BookPresentationOptionsTest {
    @TempDir Path directory;
    @Test void bothServerPermissionAndLocalPreferenceAreRequired() throws Exception {
        assertFalse(BookPresentationOptions.readLabels(directory));
        Files.writeString(directory.resolve("config/mystcraft-presentation.json"), "{\"renderlabels\":true}");
        BookPresentationOptions.loadClient(directory);
        BookPresentationOptions.acceptServer(false);
        assertFalse(BookPresentationOptions.labelsEnabled());
        BookPresentationOptions.acceptServer(true);
        assertTrue(BookPresentationOptions.labelsEnabled());
        BookPresentationOptions.acceptServer(false);
        assertFalse(BookPresentationOptions.labelsEnabled());
    }
    @Test void malformedOrNonBooleanConfigurationFailsClosed() throws Exception {
        Files.createDirectories(directory.resolve("config"));
        Path file = directory.resolve("config/mystcraft-presentation.json");
        for (String value : new String[]{"{", "[]", "{\"renderlabels\":\"true\"}", "{\"renderlabels\":1}"}) {
            Files.writeString(file, value);
            assertFalse(BookPresentationOptions.readLabels(directory), value);
        }
    }
}
