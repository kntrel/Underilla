package com.kntrel.mc.underilla.core.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jkantrell.nbt.tag.CompoundTag;
import com.jkantrell.nbt.tag.StringTag;
import com.kntrel.mc.underilla.core.api.Biome;
import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.impl.TestBlock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChunkReaderTest {

    @Test
    void cachesDecodedPaletteEntryAndReturnsIndependentClones() {
        CountingChunkReader reader = new CountingChunkReader();
        CompoundTag paletteEntry = new CompoundTag();

        TestBlock first = (TestBlock) reader.blockFromTag(paletteEntry).orElseThrow();
        TestBlock second = (TestBlock) reader.blockFromTag(paletteEntry).orElseThrow();

        assertEquals(1, reader.decodeCount);
        assertNotSame(first, second);

        first.waterlog();
        assertTrue(first.isWaterlogged());
        assertFalse(second.isWaterlogged());
    }

    private static final class CountingChunkReader extends ChunkReader {
        private int decodeCount;

        private CountingChunkReader() { super(null); }

        @Override
        protected Optional<Block> decodeBlockFromTag(CompoundTag tag) {
            decodeCount++;
            return Optional.of(TestBlock.waterloggable("stone", true));
        }

        @Override
        public Optional<Block> blockFromTag(CompoundTag tag, CompoundTag entityTag) {
            return blockFromTag(tag);
        }

        @Override
        public Optional<Biome> biomeFromTag(StringTag tag) { return Optional.empty(); }
    }
}
