package com.kntrel.mc.underilla.core.inspector;

import com.kntrel.mc.underilla.core.inspector.InspectorGenerator.GenerationStage;
import com.kntrel.mc.underilla.core.inspector.InspectorGenerator.GenerationTiming;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Collects rendered world slices from selected Inspector stages and custom stages. */
public final class StageImageSet {

    private static final int PIXELS_PER_BLOCK = 2;
    private static final int LABEL_PADDING = 8;
    private static final Color LABEL_BACKGROUND = new Color(32, 32, 32);
    private static final Color LABEL_FOREGROUND = Color.WHITE;
    private static final Font LABEL_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 16);

    private final Map<String, BufferedImage> images = new LinkedHashMap<>();
    private final Map<String, Bounds> bounds = new LinkedHashMap<>();
    private final boolean biomeOverlay;

    private StageImageSet(boolean biomeOverlay) {
        this.biomeOverlay = biomeOverlay;
    }

    public static Builder with(InspectorGenerator generator) {
        return new Builder(generator);
    }

    /**
     * Adds a slice to a custom stage. Slices with the same generated key are composited
     * using their world-coordinate bounds.
     */
    public synchronized void stage(String stage, WorldSlice slice, float index) {
        Objects.requireNonNull(stage, "stage");
        if (stage.isBlank()) {
            throw new IllegalArgumentException("stage must not be blank");
        }
        this.stage(index + " " + stage, slice);
    }

    /** Returns an unmodifiable snapshot of the images keyed by their stage labels. */
    public synchronized Map<String, BufferedImage> images() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(images));
    }

    /**
     * Renders all registered stage images, in registration order, as a labeled
     * vertical strip.
     *
     * @throws IllegalStateException if no stage images have been registered
     */
    public synchronized BufferedImage composite() {
        if (images.isEmpty()) {
            throw new IllegalStateException("Cannot compose an empty stage image set");
        }

        BufferedImage measuringImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D measuringGraphics = measuringImage.createGraphics();
        int labelHeight;
        int width = 0;
        try {
            measuringGraphics.setFont(LABEL_FONT);
            FontMetrics fontMetrics = measuringGraphics.getFontMetrics();
            labelHeight = Math.addExact(
                    fontMetrics.getHeight(),
                    Math.multiplyExact(LABEL_PADDING, 2));
            for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
                width = Math.max(width, entry.getValue().getWidth());
                width = Math.max(
                        width,
                        Math.addExact(
                                fontMetrics.stringWidth(entry.getKey()),
                                Math.multiplyExact(LABEL_PADDING, 2)));
            }
        } finally {
            measuringGraphics.dispose();
        }

        int height = 0;
        for (BufferedImage image : images.values()) {
            height = Math.addExact(height, Math.addExact(labelHeight, image.getHeight()));
        }

        BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = composite.createGraphics();
        try {
            graphics.setFont(LABEL_FONT);
            graphics.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics fontMetrics = graphics.getFontMetrics();

            int y = 0;
            for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
                graphics.setColor(LABEL_BACKGROUND);
                graphics.fillRect(0, y, width, labelHeight);
                graphics.setColor(LABEL_FOREGROUND);
                graphics.drawString(
                        entry.getKey(),
                        LABEL_PADDING,
                        y + LABEL_PADDING + fontMetrics.getAscent());

                BufferedImage image = entry.getValue();
                int imageX = (width - image.getWidth()) / 2;
                graphics.drawImage(image, imageX, y + labelHeight, null);
                y += labelHeight + image.getHeight();
            }
        } finally {
            graphics.dispose();
        }
        return composite;
    }

    private synchronized void stage(String key, WorldSlice slice) {
        Objects.requireNonNull(slice, "slice");
        Bounds sliceBounds = Bounds.of(slice);
        Bounds oldBounds = bounds.get(key);
        Bounds imageBounds = oldBounds == null ? sliceBounds : oldBounds.union(sliceBounds);
        BufferedImage image = images.get(key);

        if (image == null || !imageBounds.equals(oldBounds)) {
            BufferedImage expanded = image(imageBounds);
            if (image != null) {
                Graphics2D graphics = expanded.createGraphics();
                try {
                    graphics.drawImage(
                            image,
                            Math.multiplyExact(oldBounds.minimumX() - imageBounds.minimumX(), PIXELS_PER_BLOCK),
                            Math.multiplyExact(imageBounds.maximumY() - oldBounds.maximumY(), PIXELS_PER_BLOCK),
                            null);
                } finally {
                    graphics.dispose();
                }
            }
            image = expanded;
            images.put(key, image);
            bounds.put(key, imageBounds);
        }

        BufferedImage sliceImage = WorldSliceRenderer.render(slice, PIXELS_PER_BLOCK, biomeOverlay);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.drawImage(
                    sliceImage,
                    Math.multiplyExact(sliceBounds.minimumX() - imageBounds.minimumX(), PIXELS_PER_BLOCK),
                    Math.multiplyExact(imageBounds.maximumY() - sliceBounds.maximumY(), PIXELS_PER_BLOCK),
                    null);
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage image(Bounds bounds) {
        return new BufferedImage(
                Math.multiplyExact(bounds.width(), PIXELS_PER_BLOCK),
                Math.multiplyExact(bounds.height(), PIXELS_PER_BLOCK),
                BufferedImage.TYPE_INT_RGB);
    }

    public static final class Builder {

        private final InspectorGenerator generator;
        private final EnumMap<GenerationTiming, EnumSet<GenerationStage>> includedStages;
        private float baseIndex;
        private boolean biomeOverlay;

        private Builder(InspectorGenerator generator) {
            this.generator = Objects.requireNonNull(generator, "generator");
            this.includedStages = new EnumMap<>(GenerationTiming.class);
            for (GenerationTiming timing : GenerationTiming.values()) {
                includedStages.put(timing, EnumSet.noneOf(GenerationStage.class));
            }
        }

        public Builder baseIndex(float baseIndex) {
            if (!Double.isFinite(baseIndex)) {
                throw new IllegalArgumentException("baseIndex must be finite");
            }
            this.baseIndex = baseIndex;
            return this;
        }

        /** Enables or disables the translucent biome overlay on staged images. */
        public Builder biomeOverlay(boolean biomeOverlay) {
            this.biomeOverlay = biomeOverlay;
            return this;
        }

        public Builder include(GenerationTiming timing, GenerationStage stage) {
            includedStages.get(Objects.requireNonNull(timing, "timing"))
                    .add(Objects.requireNonNull(stage, "stage"));
            return this;
        }

        public StageImageSet build() {
            StageImageSet stageImageSet = new StageImageSet(biomeOverlay);
            for (GenerationTiming timing : GenerationTiming.values()) {
                InspectorGenerator.StageHook hook = timing == GenerationTiming.AFTER
                        ? generator.hook().after()
                        : generator.hook().before();
                for (GenerationStage stage : includedStages.get(timing)) {
                    float index = baseIndex
                            + stage.index() * 2
                            + (timing == GenerationTiming.AFTER ? 1 : 0);
                    String stageName = timing.name().toLowerCase(Locale.ROOT)
                            + " "
                            + stage.name().toLowerCase(Locale.ROOT);
                    subscribe(hook, stage, slice -> stageImageSet.stage(stageName, slice, index));
                }
            }
            return stageImageSet;
        }

        private void subscribe(
                InspectorGenerator.StageHook hook,
                GenerationStage stage,
                Consumer<WorldSlice> consumer) {
            switch (stage) {
                case NOISE -> hook.noise(consumer);
                case SURFACE -> hook.surface(consumer);
                case CAVES -> hook.caves(consumer);
                case FEATURES -> hook.features(consumer);
                case LOAD -> hook.load(consumer);
            }
        }
    }

    private record Bounds(int minimumX, int minimumY, int maximumX, int maximumY) {

        private static Bounds of(WorldSlice slice) {
            return new Bounds(
                    slice.getOffsetX(),
                    slice.getOffsetY(),
                    Math.addExact(slice.getOffsetX(), slice.getWidth()),
                    Math.addExact(slice.getOffsetY(), slice.getHeight()));
        }

        private Bounds union(Bounds other) {
            return new Bounds(
                    Math.min(minimumX, other.minimumX),
                    Math.min(minimumY, other.minimumY),
                    Math.max(maximumX, other.maximumX),
                    Math.max(maximumY, other.maximumY));
        }

        private int width() {
            return Math.subtractExact(maximumX, minimumX);
        }

        private int height() {
            return Math.subtractExact(maximumY, minimumY);
        }
    }
}
