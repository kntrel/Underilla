package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.Biome;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;

/** Renders a block slice as a PNG with world Y increasing upward. */
public final class WorldSliceRenderer {

    private WorldSliceRenderer() {}

    public static BufferedImage render(WorldSlice slice, int pixelsPerBlock) {
        return render(slice, pixelsPerBlock, false);
    }

    /**
     * Renders a slice, optionally drawing its biome cells as a translucent overlay.
     * Missing biome data does not alter the corresponding block pixels.
     */
    public static BufferedImage render(WorldSlice slice, int pixelsPerBlock, boolean biomeOverlay) {
        Objects.requireNonNull(slice, "slice");
        if (pixelsPerBlock < 1) {
            throw new IllegalArgumentException("pixelsPerBlock must be positive");
        }
        BufferedImage image = new BufferedImage(
                Math.multiplyExact(slice.getWidth(), pixelsPerBlock),
                Math.multiplyExact(slice.getHeight(), pixelsPerBlock),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int localX = 0; localX < slice.getWidth(); localX++) {
                int x = slice.getOffsetX() + localX;
                for (int localY = 0; localY < slice.getHeight(); localY++) {
                    int y = slice.getOffsetY() + localY;
                    Block block = slice.getBlock(x, y);
                    graphics.setColor(block == null ? BlockColors.AIR : BlockColors.get(block.id()));
                    graphics.fillRect(
                            localX * pixelsPerBlock,
                            (slice.getHeight() - 1 - localY) * pixelsPerBlock,
                            pixelsPerBlock,
                            pixelsPerBlock);
                    if (biomeOverlay) {
                        Biome biome = slice.getBiome(x, y);
                        if (biome != null) {
                            Color color = BiomeColors.get(biome.id());
                            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 128));
                            graphics.fillRect(
                                    localX * pixelsPerBlock,
                                    (slice.getHeight() - 1 - localY) * pixelsPerBlock,
                                    pixelsPerBlock,
                                    pixelsPerBlock);
                        }
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public static void writePng(WorldSlice slice, Path output, int pixelsPerBlock) throws IOException {
        writePng(render(slice, pixelsPerBlock), output);
    }

    /** Writes a slice PNG, optionally with its translucent biome overlay. */
    public static void writePng(WorldSlice slice, Path output, int pixelsPerBlock, boolean biomeOverlay)
            throws IOException {
        writePng(render(slice, pixelsPerBlock, biomeOverlay), output);
    }

    public static void writePng(BufferedImage image, Path output) throws IOException {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(output, "output");
        Path absoluteOutput = output.toAbsolutePath();
        Files.createDirectories(absoluteOutput.getParent());
        if (!ImageIO.write(image, "png", absoluteOutput.toFile())) {
            throw new IOException("No PNG image writer is available");
        }
    }

}
