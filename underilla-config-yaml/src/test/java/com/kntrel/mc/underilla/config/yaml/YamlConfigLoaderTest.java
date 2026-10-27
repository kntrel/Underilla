package com.kntrel.mc.underilla.config.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kntrel.mc.underilla.config.model.DiagnosticsConfig;
import com.kntrel.mc.underilla.config.model.HorizontalBound;
import com.kntrel.mc.underilla.config.model.MergeConfig;
import com.kntrel.mc.underilla.config.dsl.Compiler;
import com.kntrel.mc.underilla.config.dsl.Grammar;
import com.kntrel.mc.underilla.config.dsl.TypeLiteral;
import com.kntrel.mc.underilla.config.model.AreaConfig;
import com.kntrel.mc.underilla.config.model.PerformanceConfig;
import com.kntrel.mc.underilla.config.context.BiomeContext;
import com.kntrel.mc.underilla.config.context.BlockContext;
import com.kntrel.mc.underilla.config.context.DualBiomeContext;
import com.kntrel.mc.underilla.config.context.DualBlockContext;
import com.kntrel.mc.underilla.config.context.EntityContext;
import com.kntrel.mc.underilla.core.api.scope.EntitySnapshot;
import com.kntrel.mc.underilla.config.entity.Entity;
import com.kntrel.mc.underilla.config.context.StructureContext;
import com.kntrel.mc.underilla.config.model.UnderillaConfig;
import com.kntrel.mc.underilla.config.model.WorldSource;
import com.kntrel.mc.underilla.config.model.WorldsConfig;
import com.kntrel.mc.underilla.core.api.ID;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

class YamlConfigLoaderTest {
    private final YamlConfigLoader loader = configuredLoader();

    // Test-only compilers treat each entire expression as one opaque boolean token, without UCEL grammar.
    private static <C> Compiler<C> stubCompiler(Function<String, Boolean> decode) {
        return new Compiler<>(new Grammar<>(List.of(
                TypeLiteral.of(Boolean.class, slice -> slice.length(), decode)), List.of()));
    }

    private static YamlConfigLoader configuredLoader() {
        return configuredLoader(new YamlConfigLoader());
    }

    private static YamlConfigLoader configuredLoader(YamlConfigLoader loader) {
        return loader
                .withCompiler(BlockContext.class, stubCompiler(expression -> !expression.equals("false")))
                .withCompiler(DualBlockContext.class, stubCompiler(expression -> !expression.equals("false")))
                .withCompiler(DualBiomeContext.class, stubCompiler(expression -> !expression.equals("false")))
                .withCompiler(BiomeContext.class, stubCompiler(expression -> !expression.equals("false")))
                .withCompiler(StructureContext.class, stubCompiler(expression -> !expression.equals("false")))
                .withCompiler(EntityContext.class, stubCompiler(expression -> !expression.equals("false")));
    }

    private static void assertDefaults(UnderillaConfig config) {
        assertEquals(WorldsConfig.defaults(), config.worlds());
        assertEquals(AreaConfig.defaults(), config.area());
        assertEquals(PerformanceConfig.defaults(), config.performance());
        assertEquals(DiagnosticsConfig.defaults(), config.diagnostics());
        assertEquals(MergeConfig.Strategy.SURFACE, config.merge().strategy());
        assertFalse(config.merge().keepSourceIf().test(null));
        assertTrue(config.merge().surface().passthroughBlockIf().test(null));
        assertTrue(config.biomes().target().preserveIf().test(null));
        assertTrue(config.generation().structures().allowIf().test(null));
        assertEquals(3, config.cleanup().blocks().supportBlocks().size());
        assertTrue(config.cleanup().blocks().supportBlocks().getFirst().condition().test(null));
        assertTrue(config.cleanup().entities().removeIf().test(null));
    }

    @Test
    void compilerAttachmentsAreImmutableAndOnlyRunDuringLoading() {
        var calls = new AtomicInteger();
        var compiler = Compiler.of(Grammar.with(EntityContext.class).runtime(Boolean.class, slice -> {
            assertEquals("entity.id = @minecraft:item", slice.toString());
            calls.incrementAndGet();
            return slice.length();
        }, entity -> entity.entity().id().equals(ID.of("minecraft:item"))).done());
        var configured = loader.withCompiler(EntityContext.class, compiler);
        assertEquals(0, calls.get());
        var config = configured.loadDefaults();
        var item = EntityContext.of(null, null, Entity.of(new EntitySnapshot(ID.of("minecraft:item")), java.util.Set.of()));
        var pig = EntityContext.of(null, null, Entity.of(new EntitySnapshot(ID.of("minecraft:pig")), java.util.Set.of()));
        assertEquals(1, calls.get());
        assertTrue(config.cleanup().entities().removeIf().test(item));
        assertFalse(config.cleanup().entities().removeIf().test(pig));
        assertEquals(1, calls.get(), "Evaluation must not compile again");
        assertTrue(loader.loadDefaults().cleanup().entities().removeIf().test(pig));
    }

    @Test
    void sourceAndTargetBlockSettingsShareTheCompilerRegisteredForTheirContextType() {
        var sharedCalls = new AtomicInteger();
        var config = loader.withCompiler(BlockContext.class, stubCompiler(expression -> {
            if (expression.equals("local-rule")) sharedCalls.incrementAndGet();
            return true;
        })).load("""
                worlds:
                  source:
                    replace-blocks:
                      - if: local-rule
                        with: stone
                cleanup:
                  blocks:
                    support-blocks:
                      - if: local-rule
                        with: stone
                """);
        assertEquals(2, sharedCalls.get());
        assertTrue(config.worlds().source().replaceBlocks().getFirst().condition().test(null));
        assertTrue(config.cleanup().blocks().supportBlocks().getFirst().condition().test(null));
    }

    @Test
    void singleAndDualContextsResolveTheirOwnCompilers() {
        var config = loader.withCompiler(BlockContext.class, stubCompiler(_ -> false))
                .withCompiler(DualBlockContext.class, stubCompiler(_ -> true)).loadDefaults();
        assertFalse(config.merge().surface().passthroughBlockIf().test(null));
        assertFalse(config.cleanup().blocks().supportBlocks().getFirst().condition().test(null));
        assertTrue(config.merge().keepSourceIf().test(null));
    }

    @Test
    void missingContextCompilerFailsWithTheSettingPath() {
        var missing = assertThrows(ConfigMappingException.class, () -> new YamlConfigLoader().loadDefaults());
        assertEquals("merge.surface.passthrough-block-if[0]", missing.path());
        assertTrue(missing.getMessage().contains("No DSL compiler"));
    }

    @Test
    void compilerFailuresLogTheDetailedErrorAndUseDefaultsEvenAfterAnAlwaysTrueEntry() {
        var messages = new ArrayList<String>();
        var failure = new IllegalArgumentException("Unsupported test expression");
        var configured = configuredLoader(new YamlConfigLoader(recordingLogger(messages)))
                .withCompiler(StructureContext.class, stubCompiler(expression -> {
            if (expression.equals("invalid")) throw failure;
            return true;
        }));
        var config = configured.load("generation: {structures: {enabled: false, allow-if: [true, invalid]}}");
        assertTrue(config.generation().structures().allowIf().test(null));
        assertFalse(config.generation().structures().enabled());
        assertEquals(2, messages.size());
        assertTrue(messages.get(0).startsWith("error: Invalid selector at generation.structures.allow-if[1]:"));
        assertTrue(messages.get(0).contains("Unsupported test expression"));
        assertTrue(messages.get(0).contains("\ninvalid\n^~~~~~~"));
        assertEquals("warn: Falling back to the bundled default for 'generation.structures.allow-if'.", messages.get(1));
    }

    @Test
    void independentInvalidSelectorsEachFallBackWithoutLosingOtherSettings() {
        var messages = new ArrayList<String>();
        var configured = configuredLoader(new YamlConfigLoader(recordingLogger(messages)))
                .withCompiler(StructureContext.class, Compiler.of(Grammar.with(StructureContext.class).standard().done()))
                .withCompiler(BiomeContext.class, Compiler.of(Grammar.with(BiomeContext.class).standard().done()));
        var config = configured.load("""
                generation:
                  population: {enabled: false}
                  structures: {allow-if: unknown}
                  carvers: {preserve-liquids: false, allow-if: '1 + true'}
                """);
        assertFalse(config.generation().population().enabled());
        assertFalse(config.generation().carvers().preserveLiquids());
        assertTrue(config.generation().structures().allowIf().test(null));
        assertTrue(config.generation().carvers().allowIf().test(null));
        assertEquals(4, messages.size());
        assertTrue(messages.get(0).contains("Unrecognized operand at 0: unknown"));
        assertTrue(messages.get(2).contains("Incompatible types"));
    }

    @Test
    void failureToCompileTheBundledDefaultIsNotRetriedAsAnotherFallback() {
        var messages = new ArrayList<String>();
        var configured = configuredLoader(new YamlConfigLoader(recordingLogger(messages)))
                .withCompiler(StructureContext.class, stubCompiler(expression -> {
                    throw new IllegalArgumentException("broken compiler");
                }));
        var failure = assertThrows(IllegalStateException.class,
                () -> configured.load("generation: {structures: {allow-if: invalid}}"));
        assertEquals("Cannot load bundled default for 'generation.structures.allow-if'.", failure.getMessage());
        assertTrue(failure.getCause() instanceof ConfigMappingException);
        assertEquals(2, messages.size());
    }

    private static Logger recordingLogger(List<String> messages) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] {Logger.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("error") || method.getName().equals("warn")) {
                        Object[] values = Arrays.copyOfRange(arguments, 1, arguments.length);
                        messages.add(method.getName() + ": " + MessageFormatter.arrayFormat((String) arguments[0], values).getMessage());
                    }
                    if (method.getReturnType() == boolean.class) return true;
                    if (method.getName().equals("getName")) return "test";
                    return null;
                });
    }
    @Test
    void bundledYamlMapsToAnExecutableModel() throws IOException {
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(stream);
            assertDefaults(loader.load(stream));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "# Only a comment\n", "{}", "worlds: {}\nmerge: {}"})
    void missingSettingsUseNestedDefaults(String yaml) {
        assertDefaults(loader.load(yaml));
    }

    @Test
    void mapsPartialOverridesWithoutDiscardingSiblingDefaults() {
        var config = loader.load("""
                worlds:
                  target:
                    name: merged
                    outside-generator: VoidWorldGenerator
                merge:
                  strategy: AbSoLuTe
                  absolute:
                    boundary: 80
                  surface:
                    adaptive-depth:
                      enabled: false
                biomes:
                  source:
                    top-only: false
                  target:
                    outside-source-terrain: false
                generation:
                  population:
                    enabled: false
                  carvers:
                    preserve-liquids: false
                performance:
                  cache:
                    region-files: 8
                diagnostics:
                  debug: true
                  progress-interval-seconds: -1
                  inspection:
                    axis: X
                    slice-coordinate: -12
                    start-chunk: -2
                    length-chunks: 3
                    biome-overlay: true
                    output: relative/images
                """);
        var defaults = loader.loadDefaults();
        assertEquals(new WorldsConfig.Target("merged", "VoidWorldGenerator"), config.worlds().target());
        assertEquals(defaults.worlds().source(), config.worlds().source());
        assertEquals(MergeConfig.Strategy.ABSOLUTE, config.merge().strategy());
        assertEquals(new MergeConfig.Absolute(80, 8), config.merge().absolute());
        assertFalse(config.merge().surface().adaptiveDepth().enabled());
        assertFalse(config.biomes().source().topOnly());
        assertFalse(config.biomes().target().outsideSourceTerrain());
        assertFalse(config.generation().population().enabled());
        assertTrue(config.generation().carvers().enabled());
        assertFalse(config.generation().carvers().preserveLiquids());
        assertEquals(64, config.performance().cache().chunks());
        assertEquals(8, config.performance().cache().regionFiles());
        assertTrue(config.diagnostics().debug());
        assertFalse(config.diagnostics().progressEnabled());
        assertEquals(new DiagnosticsConfig.Inspection(false, DiagnosticsConfig.Axis.X, -12, -2,
                3, true, Path.of("relative/images")), config.diagnostics().inspection());
    }

    @Test
    void mapsDirectAndSavedWorldSourcesWithFlattenedMetadata() {
        var config = loader.load("""
                worlds:
                  source:
                    source:
                      region-path: legacy/region
                      entities-path: legacy/entities
                      name: Legacy source
                      min-height: 0
                      max-height: 256
                  underground:
                    type: world
                    source:
                      world-path: generated
                      dimension: custom:deep
                      min-height: -128
                """);
        assertEquals(new WorldSource.RegionDirectory(Path.of("legacy/region"), Path.of("legacy/entities"),
                new WorldSource.Metadata("Legacy source", 0, 256)), config.worlds().source().source());
        assertEquals(new WorldSource.SavedWorld(Path.of("generated"), ID.of("custom:deep"), null,
                new WorldSource.Metadata(null, -128, null)), config.worlds().underground().source());
        assertEquals(WorldsConfig.UndergroundType.WORLD, config.worlds().underground().type());
    }

    @Test
    void omittedDimensionDefaultsToOverworldAndSourceMayBeNullForVanilla() {
        var config = loader.load("""
                worlds:
                  source:
                    source:
                      world-path: custom
                  underground:
                    source: null
                """);
        assertEquals(ID.of("minecraft:overworld"), ((WorldSource.SavedWorld) config.worlds().source().source()).dimension());
        assertNull(config.worlds().underground().source());
    }

    @Test
    void mapsAutoAndSignedIntegerBounds() {
        var area = loader.load("""
                area:
                  min-x: auto
                  min-z: -32
                  max-x: AUTO
                  max-z: 0
                  min-y: -128
                  max-y: 256
                """).area();
        assertEquals(HorizontalBound.auto(), area.minX());
        assertEquals(HorizontalBound.auto(), area.maxX());
        assertEquals(HorizontalBound.fixed(-32), area.minZ());
        assertEquals(HorizontalBound.fixed(0), area.maxZ());
        assertEquals(-128, area.minY());
        assertEquals(256, area.maxY());
    }

    @Test
    void normalizesSelectorBooleansFoldedScalarsAndListsWhileRetainingReplacementOrder() {
        var config = loader.load("""
                worlds:
                  source:
                    replace-blocks:
                      - if: block.id = @minecraft:sand
                        with: sandstone
                      - if: [false, true]
                        with: minecraft:dirt
                merge:
                  surface:
                    passthrough-block-if: []
                  keep-source-if: >-
                    block.id = @minecraft:stone
                    and target.block.id != @minecraft:air
                biomes:
                  target:
                    preserve-if: false
                generation:
                  structures:
                    allow-if: true
                  carvers:
                    allow-if:
                      - biome.id != @minecraft:desert
                      - true
                cleanup:
                  blocks:
                    support-blocks: []
                    replace-blocks:
                      - if: true
                        with: air
                  illegal-blocks:
                    replace-blocks: []
                  entities:
                    remove-if: false
                """);
        var rules = config.worlds().source().replaceBlocks();
        assertEquals(ID.of("minecraft:sandstone"), rules.getFirst().with());
        assertTrue(rules.getFirst().condition().test(null));
        assertTrue(rules.get(1).condition().test(null));
        assertEquals(ID.of("minecraft:dirt"), rules.get(1).with());
        assertFalse(config.merge().surface().passthroughBlockIf().test(null));
        assertTrue(config.merge().keepSourceIf().test(null));
        assertFalse(config.biomes().target().preserveIf().test(null));
        assertTrue(config.generation().structures().allowIf().test(null));
        assertTrue(config.generation().carvers().allowIf().test(null));
        assertTrue(config.cleanup().blocks().supportBlocks().isEmpty());
        assertEquals(ID.of("minecraft:air"), config.cleanup().blocks().replaceBlocks().getFirst().with());
        assertTrue(config.cleanup().illegalBlocks().replaceBlocks().isEmpty());
        assertFalse(config.cleanup().entities().removeIf().test(null));
        assertThrows(UnsupportedOperationException.class, () -> rules.clear());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            unknown: true | unknown
            area: {min-z: 1} | area.min-z
            area: {max-y: -128} | area
            merge: {strategy: experimental} | merge.strategy
            merge: {surface: {adaptive-depth: {max-depth: 1}}} | merge.surface.adaptive-depth
            performance: {cache: {chunks: 0}} | performance.cache
            performance: {cache: {chunks: 1.5}} | performance.cache.chunks
            diagnostics: {debug: 'true'} | diagnostics.debug
            diagnostics: {progress-interval-seconds: 2147483648} | diagnostics.progress-interval-seconds
            diagnostics: {progress-interval-seconds: 9999999999999999999999999999} | diagnostics.progress-interval-seconds
            diagnostics: {inspection: {length-chunks: 0}} | diagnostics.inspection
            generation: {structures: {allow-if: 42}} | generation.structures.allow-if
            generation: {structures: {allow-if: [true, 42]}} | generation.structures.allow-if[1]
            generation: {structures: {allow-if: ['']}} | generation.structures.allow-if[0]
            generation: {structures: {allow-if: null}} | generation.structures.allow-if
            generation: {structures: {allow-iff: true}} | generation.structures.allow-iff
            worlds: {source: {source: {region-path: region, world-path: world}}} | worlds.source.source
            worlds: {source: {source: {region-path: region, dimension: overworld}}} | worlds.source.source
            worlds: {source: {source: {dimension: Bad:ID}}} | worlds.source.source.dimension
            worlds: {source: {source: {region-path: null}}} | worlds.source.source.region-path
            worlds: {source: {source: {min-height: 320, max-height: 0}}} | worlds.source.source
            worlds: {source: {source: null}} | worlds.source
            worlds: {underground: {type: world, source: null}} | worlds.underground
            cleanup: {blocks: {replace-blocks: [{if: true}]}} | cleanup.blocks.replace-blocks[0].with
            cleanup: {blocks: {replace-blocks: [{with: stone}]}} | cleanup.blocks.replace-blocks[0].if
            cleanup: {blocks: {replace-blocks: [{if: true, with: Bad:ID}]}} | cleanup.blocks.replace-blocks[0].with
            cleanup: {blocks: {replace-blocks: {if: true, with: stone}}} | cleanup.blocks.replace-blocks
            cleanup: null | cleanup
            [true] | $
            {1: true} | $
            """)
    void rejectsInvalidSettingsWithTheirFullYamlPath(String yaml, String path) {
        var exception = assertThrows(ConfigMappingException.class, () -> loader.load(yaml));
        assertEquals(path, exception.path(), exception.getMessage());
        assertTrue(exception.getMessage().startsWith(path + ":"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "diagnostics:\n  debug: true\n  debug: false",
            "worlds: [",
            "{}\n---\n{}",
            "!!java.util.ArrayList []"
    })
    void rejectsDuplicateKeysMalformedYamlMultipleDocumentsAndObjectTags(String yaml) {
        var exception = assertThrows(ConfigMappingException.class, () -> loader.load(yaml));
        assertEquals("$", exception.path());
        assertNotNull(exception.getCause());
    }

    @Test
    void loadsFilesAndPreservesRelativeConfigurationPaths(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("config.yml");
        Files.writeString(file, "diagnostics: {inspection: {output: ./images}}", StandardCharsets.UTF_8);
        assertEquals(Path.of("./images"), loader.load(file).diagnostics().inspection().output());
    }

    @Test
    void leavesCallerOwnedReadersAndBomEncodedStreamsOpen() {
        class TrackedReader extends StringReader {
            boolean closed;
            TrackedReader() { super("{}"); }
            @Override public void close() { closed = true; super.close(); }
        }
        class TrackedStream extends ByteArrayInputStream {
            boolean closed;
            TrackedStream() { super("\uFEFFworlds: {target: {name: Montaña}}".getBytes(StandardCharsets.UTF_8)); }
            @Override public void close() throws IOException { closed = true; super.close(); }
        }
        var reader = new TrackedReader();
        var stream = new TrackedStream();
        assertDefaults(loader.load(reader));
        assertEquals("Montaña", loader.load(stream).worlds().target().name());
        assertFalse(reader.closed);
        assertFalse(stream.closed);
    }
}
