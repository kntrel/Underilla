package com.kntrel.mc.underilla.core.reader;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kntrel.mc.underilla.core.api.GenerationLogger;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestDiskWorldReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskWorldReaderTest {

    private static final GenerationLogger NO_OP_LOGGER = new GenerationLogger() {
        @Override
        public void warning(String message) {}

        @Override
        public void error(String message, Throwable cause) {}
    };
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsTheProvidedRegionDirectoryWithoutWorldOrDimensionAssumptions() throws Exception {
        Path regionDirectory = Files.createDirectories(temporaryDirectory.resolve("arbitrary/source/terrain"));
        Files.copy(resourcePath("mca/surface.mca"), regionDirectory.resolve("r.0.0.mca"), REPLACE_EXISTING);

        TestDiskWorldReader reader = new TestDiskWorldReader(
                regionDirectory.toFile(), 1, NO_OP_LOGGER,
                new TestBlockFactory(TestBlock.air("minecraft:air")));

        assertEquals("minecraft:stone", reader.blockAt(0, 0, 0).orElseThrow().getName());
    }

    @Test
    void rejectsAMissingRegionDirectory() {
        Path missingRegionDirectory = temporaryDirectory.resolve("world/region");

        assertThrows(NoSuchFieldException.class, () -> new TestDiskWorldReader(
                missingRegionDirectory.toFile(), 1, NO_OP_LOGGER,
                new TestBlockFactory(TestBlock.air("minecraft:air"))));
    }

    private static Path resourcePath(String path) throws URISyntaxException {
        URL resource = DiskWorldReaderTest.class.getClassLoader().getResource(path);
        assertNotNull(resource, "Missing test resource " + path);
        return Path.of(resource.toURI());
    }
}
