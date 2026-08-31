package com.kntrel.mc.underilla.paper.listener;

import com.kntrel.mc.underilla.paper.profiling.ChunkGenerationProfiler;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

/** Completes chunk profiles after Paper has run the chunk's populators. */
public final class ChunkProfilingListener implements Listener {

    private final ChunkGenerationProfiler profiler;

    public ChunkProfilingListener(ChunkGenerationProfiler profiler) {
        this.profiler = profiler;
    }

    @EventHandler
    public void onChunkPopulated(ChunkPopulateEvent event) {
        Chunk chunk = event.getChunk();
        profiler.complete(event.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
