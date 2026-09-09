# Underilla core performance audit

Audited September 7, 2026, against working tree based on commit `ff21f8ec51f2b1a39be9dfed7189e44cf2faefc0`.

**There are substantial optimization opportunities.** The clearest problem is repeated calculation of the same surface boundary at different heights. Source-block decoding, block-entity lookup, and shared cache access multiply that cost. Start by eliminating repeated analysis, then optimize the remaining reads and writes.

This audit covers the current `underilla-core` generation, reader, cache, cleanup, and profiling code, plus the Paper adapters that determine their actual costs. Production code was not changed; the existing local edits to `UnderillaFactory.java` were preserved. Findings are source-backed; the first also has an executable work-count probe. There was no live Paper throughput, allocation, disk, or contention profile, so impact rankings are estimates rather than measured shares of total generation time.

**Priorities**

| Order | Opportunity | Likely benefit | Scope / effort |
| --- | --- | --- | --- |
| 1 | Cache surface boundaries per X/Z column | Very high for SURFACE | Core; medium |
| 2 | Decode source palette states once; index block entities | High, especially complex reference chunks | Core + Paper; medium |
| 3 | Avoid unnecessary target reads and source processing | High potential across strategies | Core; small first step, larger range optimization |
| 4 | Coordinate concurrent disk-cache misses; cache missing data | High when misses or sparse edges dominate | Core reader; medium |
| 5 | Separate cache budgets and prevent deferred-batch churn | High if batches are evicted between phases | Core + config; medium |
| 6 | Reuse reference height results and chunk-local height masks | Medium, dependent on height-query volume | Core; medium |
| 7 | Reuse biome decisions; short-circuit neighbor reads | Medium after boundary caching | Core; small to medium |
| 8 | Restrict cleanup support checks to applicable blocks | Medium when cleanup is enabled | Core; small to medium |
| 9 | Reduce per-block vectors, boxing, and wrapper allocation | Secondary; profile after 1–3 | Core + Paper; small to medium |

**1. Cache the boundary, rather than each answer to “is this block above it?”**

[SurfaceWorldMask.contains](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/generation/SurfaceWorldMask.java:51) returns `y > boundaryAt(x, z)`. The boundary calculation does not depend on the query's Y coordinate. Nevertheless, [CachedWorldMask](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/generation/CachedWorldMask.java:70) caches a byte for every X/Y/Z position. Each previously unseen Y repeats the downward surface scan, biome check, and adaptive neighbor analysis.

The probe uses the actual production classes, a flat source with solid ground through Y=80, range `[-64,320)`, depth 6, and adaptive settings 50/2. Its alternative invokes the exact existing boundary method once per column and stores the result. It compares every position, without changing production source.

| Work counted | Current voxel cache | Column prototype |
| --- | ---: | ---: |
| Mask positions compared | 98,304 | 98,304 |
| Boundary calculations | 98,304 | 256 |
| Source block reads | 24,674,304 | 64,256 |
| Different mask results | — | 0 |

This is **384 times fewer boundary calculations and source reads in this isolated cold-cache probe**, not a whole-plugin speedup. A second pass over the retained current cache performs zero additional source reads: the existing cache does help repeated coordinates, but fails to share the boundary across heights.

The factory also unions the generated-height mask before the surface mask. That short circuit matters: with a generated solid height of Y=64, the probe makes 33,024 boundary calculations and 8,289,024 source reads instead of the full-volume totals. That still recalculates each column boundary 129 times. Cave copying, biome requests, and cache eviction can change the actual workload.

Implement a SURFACE-specific, bounded per-chunk array of 256 integer boundaries. A full 384-high voxel cache has 98,304 bytes of section payload per chunk; an `int[256]` needs 1,024 bytes, excluding metadata and initialization tracking. Keep the generic `CachedWorldMask` for arbitrary masks whose answers genuinely depend on Y.

Also expose a chunk-local view so the merge loop acquires cache state once. Currently every mask query enters both a global LRU monitor and a per-chunk monitor, even on hits. The expensive delegate calculation runs under the per-chunk monitor. Simply changing the stored value while retaining a global synchronized lookup per voxel leaves avoidable contention.

Preserve negative-coordinate handling, strict `y > boundary` semantics, biome exclusions, ignored blocks, and adaptive behavior. Cache entries must belong to a particular reference world/configuration and be invalidated if those inputs change. Verify cliff edges and concurrent access as well as flat terrain.

**2. Make source analysis cheap: palette metadata and indexed block entities**

Every [ChunkReader.blockAt](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/reader/ChunkReader.java:50) fetches a block state, looks up a block entity, and converts the result. The [Paper conversion](/E:/Repos/Minecraft/Underilla/underilla-paper/src/main/java/com/kntrel/mc/underilla/paper/impl/BukkitChunkReader.java:29) repeatedly parses identifiers, resolves registry entries, constructs property strings, and creates block data. Height and surface analysis pay these costs merely to ask whether a block is solid or air.

The locally cached KntNBT 2.2.2 source confirms that `Chunk.getBlockEntity`, lines 226–234, scans the chunk's block-entity list. A full pass through 98,304 positions with 100 block entities can examine roughly 9.8 million list entries before accounting for the repeated surface scans. This component is much smaller for chunks with no block entities.

Decode immutable properties such as ID, solidity, air, liquid status, and ignored-block membership once per palette entry. Use those properties for analysis. Construct writable platform block objects only when needed. Index block entities by position once per source chunk, or access them only for block states that need them.

Do not share mutable `BukkitBlock` instances across positions: `waterlog()` and spawner metadata mutate their contents. Cache immutable state/templates and clone or wrap appropriately for writes; keep per-position entity data separate. Cache size should follow palette/chunk lifetime, not grow forever with all visited blocks.

**3. Delay target reads; then introduce safe range skipping**

[ReferenceWorldPatcher](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/generation/ReferenceWorldPatcher.java:56) reads and transforms every source block and reads every target block before checking the mask. For mask-selected positions, the target read does not influence the decision at all. Outside the mask, it matters only when the transformed source block satisfies `keptSurfaceBlock`.

The first change is small: retain the source transformation, check the mask, then check the preservation predicate, and only then fetch the underground block if needed. This can eliminate up to 98,304 unnecessary target reads per full-height pass. In Paper, each target read also constructs a `BukkitBlock` wrapper.

Next, use known column boundaries and palette metadata to skip untouched underground intervals when no preservation rule applies. Treat homogeneous air sections as runs where possible. Source air above the surface sometimes must overwrite generated terrain, so stopping at the highest source solid block is not generally valid. Similarly, preservation checks apply to the transformed reference block: skipping based on the original ID can change results. Arbitrary public callbacks need an explicit purity contract or a conservative fallback before memoizing or skipping their execution.

Bulk section/range writes are a later step. They must preserve deferred-carver behavior and per-position block data. Some proxies implement `setRegion` as individual writes, so replacing a loop with that call alone does not establish a speedup.

**4. Prevent duplicate region loads and repeated negative lookups**

[DiskWorldReader.readChunk/readRegion](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/reader/DiskWorldReader.java:93) synchronizes individual cache operations, but not the entire miss/load/publish sequence. Workers missing the same region can independently read and deserialize it. The KntNBT source confirms that `MCAUtil.read(File)` loads all present chunks: `MCAFile.deserialize`, lines 51–67, visits the region's 1,024 slots and deserializes each populated one.

Use a per-region in-flight load entry so callers share the same result. Do not hold one global cache lock during disk I/O. Apply the same principle to expensive chunk conversion if profiling shows duplicate construction.

Missing regions and missing chunks are not cached. Repeated coverage checks, biome queries, or edge-neighbor probes can revisit them; absent regions incur repeated file-existence checks. Store bounded negative entries for a stable reference-world snapshot. Distinguish genuine absence from transient I/O failure, and define expiration or invalidation if the source can change.

The reader's `RLUCache` is not access-order LRU: reads do not promote entries. Existing-key insertion also calls linear `LinkedList.remove`. Consider an O(1) bounded cache with an explicit eviction policy, but measure miss rate before assuming LRU beats the current insertion ordering during region-by-region generation.

Per-chunk lazy region decoding is a larger, workload-dependent option. It can help sparse/random traversal, while eager region reads may be appropriate for sequential pregeneration. First eliminate duplicate loads and collect actual hit/miss/read-duration statistics.

**5. Give deferred work its own capacity and measure fallback recomputation**

The config describes `cache.size` as a number of regions, default 16. [DiskWorldReader](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/reader/DiskWorldReader.java:59) derives 1,024 chunk-reader entries and 16,384 biome-cell entries from that value. [PaperGenerationPlanFactory](/E:/Repos/Minecraft/Underilla/underilla-paper/src/main/java/com/kntrel/mc/underilla/paper/generation/PaperGenerationPlanFactory.java:63) passes the same value as the core chunk-cache size: only 16 mask buckets and 16 deferred batches.

The Paper factory selects the surface noodle-cave policy, so this deferred path is relevant to normal Paper generation. If more than 16 chunks are pending between the two phases, FIFO eviction can remove a batch before use. [The applier's miss path](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/patch/DeferredPatcher.java:184) reruns the delegate, including surface copying/analysis and potentially height-map calculation.

Track batches produced, consumed, evicted, recomputed, and their byte sizes. Size deferred storage for observed in-flight work with a separate memory budget; do not increase region retention just to retain more pending writes. Consider explicit phase-lifetime storage where feasible. Fallback recomputation also reads a later target state, so verify cached and evicted paths remain equivalent when changing this design.

The existing primitive arrays are a useful optimization. A full 98,304-write batch grows to capacity 131,072: its three coordinate arrays and block-reference array alone use about 2–2.5 MiB, depending on reference width, before block objects and headers. Pack local coordinates into one integer and consider palette references if compatible with mutation semantics. Retaining many more batches without a byte budget can trade recomputation for excessive memory.

**6. Reuse heights at the right lifetime**

[SurfaceAltimeter.heightAt](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/generation/SurfaceAltimeter.java:25) rescans a reference column for every request. `airSectionsBottom()` caches a chunk-wide upper starting point, not the per-column result. Cache height results by column, height-map predicate, and applicable minimum height. The current implementation has three distinct predicate behaviors across its six height-map types; preserve those behaviors unless separately correcting semantics.

[WorldHeightPatcher](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/generation/WorldHeightPatcher.java:36) separately scans all 256 generated columns and accesses a concurrent coordinate map for every subsequent mask query. A patch-local height-array view can avoid those per-block map lookups. Reusing a platform height map requires proving it has the same solid-block definition and represents the same generation phase. Generated heights must not be cached across mutations as though they were immutable reference heights.

Small additional issue: [ChunkReader.airSectionsBottom](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/reader/ChunkReader.java:120) starts its section lookup at `MAXIMUM_HEIGHT - 1` (319) rather than a section index (19). KntNBT `getSection(int)` takes a section index. That causes 300 unnecessary absent-section lookups on an ordinary first calculation. It is cached, so this is a minor fix compared with repeated full-column analysis; preserve empty-world behavior and address custom-height bounds deliberately.

**7. Reuse biome decisions and actually short-circuit neighbor reads**

The deferred-write predicate looks up the reference biome for each selected write. With top-Y-only enabled, a chunk has only 16 distinct horizontal 4×4 biome cells, yet per-block decisions repeatedly enter the shared biome cache. Precompute those 16 decisions per chunk; for vertically varying biomes, use per-section/cell decisions. Reuse source liquid metadata instead of re-reading a block already fetched for copying.

[SurfaceWorldMask.haveNonSolidNeighbour](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/generation/SurfaceWorldMask.java:91) constructs `Stream.of` from four already-executed `blockAt` calls. `anyMatch` cannot save those reads. Sequential checks can stop after the first present nonsolid neighbor. Preserve the current treatment of missing neighbors as ignored. This saves up to three reads per adaptive step where an early neighbor qualifies; it does not reduce four reads to one on every terrain type.

**8. Restrict cleanup support checks**

[BlockCleanupPatcher.cleanBlock](/E:/Repos/Minecraft/Underilla/underilla-core/src/main/java/com/kntrel/mc/underilla/core/cleanup/BlockCleanupPatcher.java:43) reads the block below almost every non-air block before discovering whether a support-replacement rule exists. Resolve rule applicability first, then inspect support only for affected blocks. With configuration-derived pure mappings, this removes many pointless reads in ordinary stone terrain.

A column traversal can also reuse the previous block's final solidity, provided it accounts for replacements already applied below. Preserve the existing bottom-up semantics, the use of the original block ID, and the ordering of support replacement followed by general replacement. Section-palette filtering and skipping truly empty rule sets are further opportunities; arbitrary callback APIs require care before evaluating functions fewer times.

**9. Reduce allocation after removing redundant work**

`VectorIterable` creates an `IntVector` per position, with boxed coordinates; the deferred proxy creates another position object per intercepted write. Paper getters create block wrappers, and block identifiers are reconstructed repeatedly. Primitive loops and chunk/section-local immutable metadata can reduce this traffic.

Treat these as candidates, not measured allocation totals: the JVM may eliminate some temporary objects. Use allocation profiling after changes 1–3, then optimize objects that actually survive compilation. These changes are less valuable than avoiding millions of underlying source reads.

**Validation and implementation order**

The included [probe](/E:/Repos/Minecraft/Underilla/audits/core-performance/SurfaceMaskProbe.java) ran successfully against freshly compiled current core sources with Java 25. Run [run-probe.ps1](/E:/Repos/Minecraft/Underilla/audits/core-performance/run-probe.ps1) from PowerShell; it uses cached KntNBT 2.2.2 and SLF4J API 2.0.17, copies dependencies into the build directory, and writes results to `build/core-performance-audit/probe-output.txt`. It does not measure timing or exercise Paper. The SLF4J version is the available local compatible API, rather than the core build's declared 2.0.16.

The standard `gradlew :underilla-core:test --offline` attempt could not reach test execution: the wrapper attempted to download its Gradle distribution and the environment denied network access. No unit-suite pass is claimed. Direct compilation succeeded with the existing unchecked-operation note in `TagInterpreter`.

Implement column-boundary caching first, then delayed target reads and cheap palette/block-entity access. Add cache and deferred-work counters before tuning their budgets. Validate output equivalence on current factory plans with real MCA fixtures as well as synthetic cliffs, negative chunk coordinates, missing neighbors, liquids, block replacements, preserved biomes, and deferred eviction. Existing `MergerCharacterizationTest` primarily exercises legacy test patchers; those checks alone do not establish equivalence of the current factory pipeline.

For performance validation, use disposable output worlds with identical seed, source, datapacks, generation area, settings, worker count, and JVM heap. Separate cold and warm source-cache cases. Compare end-to-end chunks/second, wall time, per-stage latency, allocation, GC time, cache misses, duplicate loads, and deferred recomputations. Include flat terrain, mountains/cliffs, block-entity-heavy chunks, and region boundaries. Test single-worker and realistic multi-worker runs to expose contention.

The plugin already writes cumulative `plugins/Underilla/metrics.json`. Its `chunk_generation` metric sums active Underilla callback intervals; it is not complete vanilla generation time or queue-to-completion latency. Nested patch timings overlap, and wrappers around composite patchers do not separately expose all internal costs. Add targeted stage counters/timers and sample hot biome/height instrumentation if needed; compare profiling enabled/disabled to quantify measurement overhead. The recorder already batches disk snapshots, so writing JSON per block is not a current problem.

No overall speedup percentage is justified yet. The measured duplication makes boundary caching a strong first investment; the remaining ranking should be refined from a representative server profile. Simply increasing threads or the shared cache setting can amplify contention or memory use without removing the redundant work.
