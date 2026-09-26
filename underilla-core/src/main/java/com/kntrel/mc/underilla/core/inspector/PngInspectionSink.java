package com.kntrel.mc.underilla.core.inspector;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;

/** Writes each completed inspection stage and the final sequence to a directory. */
public final class PngInspectionSink implements InspectionSink {

    private final Path output;
    private final StageImageSet images;

    public PngInspectionSink(Path output, boolean biomeOverlay) {
        this.output = Objects.requireNonNull(output, "output").toAbsolutePath().normalize();
        this.images = new StageImageSet(biomeOverlay);
        try {
            Files.createDirectories(this.output);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not prepare inspector output directory " + this.output, exception);
        }
    }

    /** Writes the source-world slice before any generated stages and includes it in the sequence. */
    public void publishReference(WorldSlice slice) {
        images.stage("reference", slice, 0);
        try {
            writePng(images.image("reference", 0), output.resolve("0.0 reference.png"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write inspector reference to " + output, exception);
        }
    }

    @Override
    public void publish(InspectionStage stage, WorldSlice slice) {
        images.stage(stage, slice);
        try {
            writePng(
                    images.image(stage),
                    output.resolve((stage.order() + 1.0f) + " " + stage.label() + ".png"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write inspector stage " + stage + " to " + output, exception);
        }
    }

    @Override
    public void complete(InspectionReport report) {
        try {
            writePng(images.composite(), output.resolve("sequence.png"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write inspector sequence to " + output, exception);
        }
    }

    static void writePng(BufferedImage image, Path output) throws IOException {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(output, "output");
        Path absoluteOutput = output.toAbsolutePath();
        Files.createDirectories(absoluteOutput.getParent());
        if (!ImageIO.write(image, "png", absoluteOutput.toFile())) {
            throw new IOException("No PNG image writer is available");
        }
    }
}
