package com.kntrel.mc.underilla.core.generation;

import com.kntrel.mc.underilla.core.api.Block;
import com.kntrel.mc.underilla.core.api.ChunkData;
import com.kntrel.mc.underilla.core.api.GenerationConstants;
import com.kntrel.mc.underilla.core.reader.ChunkReader;
import com.kntrel.mc.underilla.core.reader.WorldReader;
import com.kntrel.mc.underilla.core.vector.LocatedBlock;
import com.kntrel.mc.underilla.core.vector.Vector;
import com.kntrel.mc.underilla.core.vector.VectorIterable;
import java.util.List;

/** Shared block-copying mechanics for merge strategies based on a per-column boundary. */
abstract class AbstractMerger implements Merger {

    private final GenerationContext context;

    protected AbstractMerger(GenerationContext context) { this.context = context; }

    protected final GenerationContext context() { return context; }

    protected abstract int mergeBoundaryY(int globalX, int globalZ);

    @Override
    public final void mergeLand(ChunkReader surfaceReader, ChunkData chunkData, ChunkReader cavesReader) {
        long startTime = System.currentTimeMillis();
        int airColumn = surfaceReader.airSectionsBottom();
        chunkData.setRegion(0, airColumn, 0, GenerationConstants.CHUNK_SIZE, chunkData.getMaxHeight(),
                GenerationConstants.CHUNK_SIZE, context.blocks().air());

        VectorIterable iterable = new VectorIterable(0, GenerationConstants.CHUNK_SIZE,
                context.config().generationAreaMinY(), airColumn, 0, GenerationConstants.CHUNK_SIZE);
        int columnBoundary = context.config().maxHeightOfCaves();
        int lastX = -1;
        int lastZ = -1;
        Generator.addTime("Create VectorIterable to merge land", startTime);
        for (Vector<Integer> vector : iterable) {
            startTime = System.currentTimeMillis();
            Block customBlock = surfaceReader.blockAt(vector.x(), vector.y(), vector.z()).orElse(context.blocks().air());
            customBlock = replaceSurfaceBlockIfNecessary(customBlock);
            Generator.addTime("Read block data from custom world", startTime);

            startTime = System.currentTimeMillis();
            Block vanillaBlock = cavesReader == null ? chunkData.getBlock(vector)
                    : cavesReader.blockAt(vector.x(), vector.y(), vector.z()).orElse(context.blocks().air());
            Generator.addTime("Read block data from vanilla world", startTime);

            startTime = System.currentTimeMillis();
            if (vector.x() != lastX || vector.z() != lastZ) {
                lastX = vector.x();
                lastZ = vector.z();
                columnBoundary = mergeBoundaryY(surfaceReader.getGlobalX(vector.x()), surfaceReader.getGlobalZ(vector.z()));
            }
            Generator.addTime("Calculate lower block to remove", startTime);

            startTime = System.currentTimeMillis();
            if (vector.y() > columnBoundary || shouldKeepReferenceBlockInUnderground(customBlock, vanillaBlock)) {
                chunkData.setBlock(vector, customBlock);
            } else if (cavesReader != null) {
                chunkData.setBlock(vector, vanillaBlock);
            }
            Generator.addTime("Merge block or not", startTime);
        }
    }

    @Override
    public final void reInsertLiquids(WorldReader worldReader, ChunkData chunkData) {
        ChunkReader reader = worldReader.readChunk(chunkData.getChunkX(), chunkData.getChunkZ()).orElse(null);
        if (reader == null) {
            context.logger().warning(String.format("No reader found for chunk %d, %d. Skipping liquid reinsertion.",
                    chunkData.getChunkX(), chunkData.getChunkZ()));
            return;
        }

        List<LocatedBlock> locations = reader.locationsOf(Block::isLiquid).stream()
                .filter(location -> location.y() > mergeBoundaryY(
                        chunkData.getChunkX() * GenerationConstants.CHUNK_SIZE + location.x(),
                        chunkData.getChunkZ() * GenerationConstants.CHUNK_SIZE + location.z()))
                .toList();

        locations.forEach(location -> {
            Block block = chunkData.getBlock(location.vector());
            block.waterlog();
            chunkData.setBlock(location.vector(), block);
        });
    }

    @Override
    public final boolean isUnderground(int globalX, int y, int globalZ) { return y <= mergeBoundaryY(globalX, globalZ); }

    @Override
    public boolean shouldGenerateNoise() { return true; }

    private boolean shouldKeepReferenceBlockInUnderground(Block customBlock, Block vanillaBlock) {
        return context.config().shouldKeepSurfaceBlockInCaves(customBlock.getName())
                && (vanillaBlock == null || vanillaBlock.isSolid());
    }

    private Block replaceSurfaceBlockIfNecessary(Block block) {
        String replacement = context.config().surfaceBlockReplacement(block.getName());
        return replacement == null ? block : context.blocks().create(replacement);
    }
}
