# 3.0.0
- Requires Java 25 and targets Paper 26.2. Chunky and VoidWorldGenerator are still used for pregeneration and out-of-bounds chunks; the configured setup step can download them.
- Existing configurations need migration: replace `surfaceWorld.name` and `cavesWorld.name` with `worldPath` and `dimension` in each section. The default source layout is `<worldPath>/dimensions/minecraft/overworld/region`; use `regionPath` for a different layout. Set `surfaceWorld.entitiesPath` when entity files cannot be resolved from the world and dimension.
- Reference-world entities can now be copied from entity region files into newly generated chunks. If no entity region directory is available, generation continues without copying them.
- Replace `cache.size` with `cache.regionFiles` (default `16`) and `cache.chunks` (default `64`). The chunk cache reduces repeated source-world reads; values below `32` may substantially slow generation.
- Use namespaced block, biome, and entity IDs in configuration, such as `minecraft:sand` and `minecraft:item`. Unqualified IDs use the `minecraft` namespace. Block and entity regex patterns now match complete namespaced IDs, so existing patterns should be reviewed.
- Block support and replacement cleanup now runs during generation, and entity removal runs when a newly generated chunk loads. Remove `steps.cleaningBlocks` and the previously misspelled `steps.cleaingEntities` from existing configurations; only `steps.underillaGeneration` needs to be reset to `todo` for a new generation pass.
- Removed `clean.blocks.removeUnstableBlocks` and its unstable-block survival checks. The end-of-generation block transformer hook was also removed; plugin integrations using it must be updated.
- `vanillaPopulation.enabled` now controls mob generation as well as feature generation.
- Improved terrain and cave merging, including filling gaps between a high reference surface and lower generated terrain when no separate caves world is supplied, and preserving configured reference terrain around cave carvers.
- The bundled biome-merging list now includes `minecraft:sulfur_caves`.
- Generation timings and aggregate statistics are written to `plugins/Underilla/metrics.json` during generation and on shutdown.
- The bundled example datapacks and test worlds have been removed. Bring your own datapack when customizing vanilla features or cave generation.
- The Java integration packages moved from `fr.formiko.mc.underilla` to `com.kntrel.mc.underilla`; downstream plugins and libraries must update their imports. The project now builds separate core and Paper modules.
- Pushes to `main` build and run tests. When the project version increases, the release workflow creates a `v` tag and publishes the JAR to GitHub Releases and Hangar. Modrinth and Maven Central publishing are no longer configured.

# 2.3.4
- Support from 1.21.5 to 26.1.2.

# 2.3.1
- Make the cave biome heigh be larger for the 3 cave biome & the same for the 3. This should fix deed_dark being much more common than the 2 others one.

# 2.3.0
- Support from 1.21.5 to 1.21.11.
- Underilla now use Paper biome API and won't be compatible with Spigot.
- Drop BiomeUtils dependency for Paper API to support a larger range of version (without NMS). It fix version 2.2.1 & 2.2.2 not working in 1.21.11.

# 2.2.3
- Support from 1.21.3 to 1.21.10. This is the last version to support 1.21.4 and previous version.

# 2.2.2
- Clean tasks are now merged into the main tasks.

# 2.2.1
- Support from 1.21.3 to 1.21.11

# 2.2.0
- Support & replace blocks before the chunk is generated as part of the BlockPopulator
- Support from 1.21.3 to 1.21.10

# 2.1.9
- Support from 1.21.3 to 1.21.9

# 2.1.8
- generationArea X and Z coordinates can be set to auto and will be auto computed.

# 2.1.7
- Support from 1.21.3 to 1.21.8

# 2.1.6
- Support 1.21.5 & 1.21.6. (From 1.21.3 to 1.21.6)

# 2.1.2
- Add customizable post step actions.

# 2.1.1
- Add functions to edit biomes, blocks or entities at the end of the world generation.
- Publish to maven central gradle config (Change the group for that & remove Underilla-Spigot directory).

# 2.0.13
- Performance improvement for NONE strategy.

# 2.0.11
- Remove a custom yaml dependency

# 2.0.10
- Read `biomesMerging.fromCavesGeneration.enabled` correctly from config.
- Fix cave biome that where over surface on eroded biome.

# 2.0.9
- Config: Aera values will be swap if min > max.
