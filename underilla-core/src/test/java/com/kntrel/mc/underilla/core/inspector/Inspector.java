package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.api.ID;
import com.kntrel.mc.underilla.core.generation.NoodleCavesPolicy;
import com.kntrel.mc.underilla.core.generation.UnderillaFactory;
import com.kntrel.mc.underilla.core.generation.WorldGenerationPlan;
import com.kntrel.mc.underilla.core.impl.TestBiome;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import com.kntrel.mc.underilla.core.impl.TestBlockFactory;
import com.kntrel.mc.underilla.core.impl.TestWorld;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "inspect"
)
public class Inspector implements Callable<Integer> {

    private static final int MINIMUM_Y = -64;
    private static final int MAXIMUM_Y = 320;
    private static final int PIXELS_PER_BLOCK = 2;
    private static final ID AIR_ID = new ID("minecraft", "air");

    @Parameters(
            index = "0",
            paramLabel = "mcaFile",
            description = "Path to the .mca file to use as reference",
            defaultValue = "./underilla-core/src/test/resources/mca/surface.mca"
    )
    private File file;

    @Option(
            names = "--strategy",
            description = "Merge strategy to use: none, absolute, surface",
            paramLabel = "<none|absolute|surface>",
            defaultValue = "surface"
    )
    private Strategy strategy;

    @Option(
            names = { "--output", "-o" },
            description = "Path of to which to write the output images",
            defaultValue = "./underilla-inspector"
    )
    private Path outputPath;

    @Option(
            names = { "--seed", "-s" },
            description = "The seed of the vanilla base world",
            defaultValue = "42"
    )
    private long seed;

    @Option(
            names = { "--chunk-lenght", "-cl" },
            description = "The length in chunks of the inspected section",
            defaultValue = "32"
    )
    private int chunkSize;

    @Option(
            names = "--surface-fill",
            negatable = true
    )
    private boolean surfaceFill = true;

    @Option(
            names = "--cavers",
            negatable = true
    )
    private boolean cavers = true;

    @Option(
            names = "--features",
            negatable = true
    )
    private boolean features = true;

    @Option(
            names = "--surface-cavers",
            negatable = true
    )
    private boolean surfaceCavers = true;

    @Option(
            names = "--clean-up",
            negatable = true
    )
    private boolean cleanUp = true;

    @Override
    public Integer call() {
        try {
            this.callInner();
        } catch (Throwable _) {
            return 1;
        }
        return 0;
    }

    private void callInner() {

        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }

        BufferedImage image = new BufferedImage(
                Math.multiplyExact(Math.multiplyExact(chunkSize, GenerationConstants.CHUNK_SIZE), PIXELS_PER_BLOCK),
                Math.multiplyExact(MAXIMUM_Y - MINIMUM_Y, PIXELS_PER_BLOCK),
                BufferedImage.TYPE_INT_RGB);
        InspectorGenerator inspectorGenerator = new InspectorGenerator(
                plan(),
                new TestWorld(
                        MINIMUM_Y,
                        MAXIMUM_Y,
                        TestBlock.air("minecraft:air"),
                        new TestBiome("minecraft:plains")),
                seed,
                chunkSize,
                image);
        inspectorGenerator.generate();
        try {
            WorldSliceRenderer.writePng(image, outputPath);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write inspector image to " + outputPath, exception);
        }
    }

    private WorldGenerationPlan plan(WorldReader reference) {
        UnderillaFactory.Builder planBuilder = switch (strategy) {
            case NONE     -> UnderillaFactory.none(reference);
            case ABSOLUTE -> UnderillaFactory.absolute(reference);
            case SURFACE  -> UnderillaFactory.surface(reference);
        };

        NoodleCavesPolicy cavesPolicy = surfaceCavers
                ? NoodleCavesPolicy.surface(_ -> false, true)
                : NoodleCavesPolicy.underground();

        return planBuilder
                .carvers(cavers)
                .features(features)
                .surfaceFill(surfaceFill)
                .blocks(new TestBlockFactory(TestBlock.air(AIR_ID.toString())))
                .chunkCacheSize(chunkSize)
                .generationArea(0, 0, chunkSize, 1)
                .noodleCaves(cavesPolicy)
                .build();
    }

    static void main(String[] args) {
        int out = new CommandLine(new Inspector()).execute(args);
        System.exit(out);
    }
}
