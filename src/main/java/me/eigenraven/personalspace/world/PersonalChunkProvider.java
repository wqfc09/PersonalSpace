package me.eigenraven.personalspace.world;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.FlatLayerInfo;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;
import net.minecraft.world.gen.feature.WorldGenTrees;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import me.eigenraven.personalspace.Config;
import me.eigenraven.personalspace.PersonalSpaceMod;
import net.minecraft.world.gen.ChunkProviderGenerate;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenVillage;
import rwg.world.ChunkGeneratorRealistic;
import net.minecraftforge.event.terraingen.TerrainGen;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class PersonalChunkProvider implements IChunkProvider {

    private PersonalWorldProvider world;
    private long seed;
    private Random random;
    private IChunkProvider terrainGenerator;
    private WorldGenTrees treeGen = new WorldGenTrees(false, 4, 0, 0, false);
    private String savedBiomeName = null;
    private int savedBiomeId = -1;

    public PersonalChunkProvider(PersonalWorldProvider world, long seed) {
        this.world = world;
        this.seed = seed;
        this.random = new Random(seed);

        switch (world.getConfig().getGenerationType()) {
            case RWG:
                ChunkGeneratorRealistic rwgGen = new ChunkGeneratorRealistic(world.worldObj, seed);
                disableRwgStructures(rwgGen);
                this.terrainGenerator = rwgGen;
                break;
            case VANILLA:
                ChunkProviderGenerate vanillaGen = new ChunkProviderGenerate(world.worldObj, seed, false);
                disableVanillaStructures(vanillaGen);
                this.terrainGenerator = vanillaGen;
                break;
            case FLAT:
            default:
                this.terrainGenerator = null;
                break;
        }

        if (Config.debugLogging) {
            PersonalSpaceMod.LOG.info("PersonalChunkProvider created for world {}", world.dimensionId, new Throwable());
        }
    }

    private void disableRwgStructures(ChunkGeneratorRealistic gen) {
        try {
            replaceField(gen, "caves", new MapGenNoOp());
            replaceField(gen, "strongholdGenerator", new MapGenStrongholdNoOp());
            replaceField(gen, "mineshaftGenerator", new MapGenMineshaftNoOp());
            replaceField(gen, "villageGenerator", new MapGenVillageNoOp());
            replaceField(gen, "ravines", new MapGenNoOp());
        } catch (Exception e) {
            PersonalSpaceMod.LOG.error("Failed to disable RWG structures", e);
        }
    }

    private void replaceField(Object instance, String fieldName, Object value) throws Exception {
        Field f = instance.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(f, f.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
        f.set(instance, value);
    }

    private static class MapGenNoOp extends MapGenBase {
        @Override
        public void func_151539_a(IChunkProvider cp, World w, int x, int z, Block[] b) {
        }
    }

    private void disableVanillaStructures(ChunkProviderGenerate gen) {
        String[] caveFields = { "caveGenerator", "field_73130_m" };
        String[] ravineFields = { "ravineGenerator", "field_73128_n" };

        for (String field : caveFields) {
            try {
                replaceField(gen, field, new MapGenNoOp());
            } catch (Exception ignored) {
            }
        }
        for (String field : ravineFields) {
            try {
                replaceField(gen, field, new MapGenNoOp());
            } catch (Exception ignored) {
            }
        }
    }

    // RWG types are strictly typed, so we need matching subclasses
    private static class MapGenStrongholdNoOp extends MapGenStronghold {
        @Override
        public void func_151539_a(IChunkProvider cp, World w, int x, int z, Block[] b) {
        }
    }

    private static class MapGenMineshaftNoOp extends MapGenMineshaft {
        @Override
        public void func_151539_a(IChunkProvider cp, World w, int x, int z, Block[] b) {
        }
    }

    private static class MapGenVillageNoOp extends MapGenVillage {
        @Override
        public void func_151539_a(IChunkProvider cp, World w, int x, int z, Block[] b) {
        }
    }

    @Override
    public boolean chunkExists(int x, int z) {
        return true;
    }

    @Override
    public Chunk provideChunk(int chunkX, int chunkZ) {
        Chunk chunk;
        if (this.terrainGenerator != null) {
            chunk = this.terrainGenerator.provideChunk(chunkX, chunkZ);
        } else {
            chunk = new Chunk(this.world.worldObj, chunkX, chunkZ);
            chunk.isModified = true;

            List<FlatLayerInfo> layers = world.getConfig().getLayers();
            int y = 0;
            int worldHeight = world.getHeight();
            for (FlatLayerInfo info : layers) {
                Block block = info.func_151536_b();
                if (block == null || block == Blocks.air) {
                    y += info.getLayerCount();
                    continue;
                }
                for (; y < info.getMinY() + info.getLayerCount() && y < worldHeight; ++y) {
                    int yChunk = y >> 4;
                    ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[yChunk];
                    if (ebs == null) {
                        ebs = new ExtendedBlockStorage(y & ~15, true);
                        chunk.getBlockStorageArray()[yChunk] = ebs;
                    }
                    for (int z = 0; z < 16; ++z) {
                        for (int x = 0; x < 16; ++x) {
                            ebs.func_150818_a(x, y & 15, z, block);
                        }
                    }
                }
                if (y >= worldHeight) {
                    break;
                }
            }
        }

        if (chunkX == 0 && chunkZ == 0) {
            // Spawn Platform Generation
            int platformY = 113;
            int yChunk = platformY >> 4;
            ExtendedBlockStorage ebs = chunk.getBlockStorageArray()[yChunk];
            if (ebs == null) {
                ebs = new ExtendedBlockStorage(yChunk << 4, true);
                chunk.getBlockStorageArray()[yChunk] = ebs;
            }

            // Clear Air (114-121)
            for (int y = 114; y <= 121; y++) {
                // Ensure we stay within the same chunk section (112-127), which matches
                // 114-121.
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        ebs.func_150818_a(x, y & 15, z, Blocks.air);
                    }
                }
            }

            // Platform (113) - Obsidian
            for (int x = 4; x <= 12; x++) {
                for (int z = 4; z <= 12; z++) {
                    ebs.func_150818_a(x, platformY & 15, z, Blocks.obsidian);
                }
            }
        }

        if (savedBiomeId < 0 || !Objects.equals(savedBiomeName, world.getConfig().getBiomeId())) {
            savedBiomeName = world.getConfig().getBiomeId();
            savedBiomeId = world.getConfig().getRawBiomeId();
            // Force update biome array if using flat generator or if forcing biome
            // Actually for RWG/Vanilla we might want to respect their biomes?
            // The existing code forced the biome. Let's keep it consistent:
            // If the user selected a biome in config, applying it to the whole chunk
            // override?
            // The config has "biomeId" which defaults to Plains.
            // If user uses RWG, they probably want RWG biomes?
            // Existing code:
            // Arrays.fill(chunk.getBiomeArray(), (byte) savedBiomeId);
            // This overrides everything to one biome.
            // For RWG/Vanilla, this ruins the generation biomes?
            // But the user selected "biomes" layout in GUI only for FLAT.
            // In RWG/Vanilla, biome selection is hidden.
            // So for RWG/Vanilla, we should NOT override biomes.
        }

        // Only override biome if FLAT type, or if we really want single biome.
        // User asked to hide biome selector for Vanilla/RWG.
        // So we should only override biome if generationType == FLAT.
        if (world.getConfig().getGenerationType() == DimensionConfig.GenerationType.FLAT) {
            Arrays.fill(chunk.getBiomeArray(), (byte) savedBiomeId);
        }

        chunk.generateSkylightMap();

        return chunk;
    }

    @Override
    public Chunk loadChunk(int x, int z) {
        return this.provideChunk(x, z);
    }

    @Override
    public void populate(IChunkProvider provider, int chunkX, int chunkZ) {
        if (this.terrainGenerator != null) {
            this.terrainGenerator.populate(provider, chunkX, chunkZ);
            // We can add extra cleanup here if needed, but the generator's internal
            // structure methods were disabled.
            return;
        }

        BiomeGenBase biome = this.world.worldObj.getBiomeGenForCoords(chunkX * 16 + 16, chunkZ * 16 + 16);
        this.random.setSeed(this.seed);
        long i1 = this.random.nextLong() / 2L * 2L + 1L;
        long j1 = this.random.nextLong() / 2L * 2L + 1L;
        this.random.setSeed((long) chunkX * i1 + (long) chunkZ * j1 ^ this.seed);
        if (this.world.getConfig().isGeneratingVegetation()) {
            biome.decorate(this.world.worldObj, this.random, chunkX * 16, chunkZ * 16);
        }
        if (this.world.getConfig().isGeneratingTrees() && TerrainGen.decorate(
                world.worldObj,
                random,
                chunkX * 16,
                chunkZ * 16,
                DecorateBiomeEvent.Decorate.EventType.TREE)) {
            int x = chunkX * 16 + random.nextInt(16) + 8;
            int z = chunkZ * 16 + random.nextInt(16) + 8;
            int y = this.world.worldObj.getHeightValue(x, z);
            WorldGenAbstractTree worldgenabstracttree = BiomeGenBase.plains.func_150567_a(random);
            worldgenabstracttree.setScale(1.0D, 1.0D, 1.0D);

            if (worldgenabstracttree.generate(world.worldObj, random, x, y, z)) {
                worldgenabstracttree.func_150524_b(world.worldObj, random, x, y, z);
            }
        }
    }

    @Override
    public boolean saveChunks(boolean saveAllChunks, IProgressUpdate progress) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "PersonalWorldSource";
    }

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return Collections.emptyList();
    }

    // findClosestStructure
    @Override
    public ChunkPosition func_147416_a(World world, String structureType, int x, int y, int z) {
        return null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int x, int z) {
    }

    @Override
    public void saveExtraData() {
    }
}
