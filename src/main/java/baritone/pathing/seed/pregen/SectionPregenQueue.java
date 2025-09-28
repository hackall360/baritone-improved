package baritone.pathing.seed.pregen;

import baritone.api.pathing.seed.SeedOreMode;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public final class SectionPregenQueue {

    private static ExecutorService executor;
    private static SectionStore store;
    private static final Set<Long> seen = ConcurrentHashMap.newKeySet();

    private SectionPregenQueue() {
    }

    public static void init(ExecutorService service, SectionStore sectionStore) {
        executor = service;
        store = sectionStore;
        seen.clear();
    }

    public static void queueAround(GeneratorContext context, int playerChunkX, int playerChunkZ, int radius, SeedOreMode mode) {
        if (executor == null || store == null) {
            return;
        }
        int minChunkX = playerChunkX - radius;
        int maxChunkX = playerChunkX + radius;
        int minChunkZ = playerChunkZ - radius;
        int maxChunkZ = playerChunkZ + radius;
        int minSectionY = SectionUtil.minSectionY(context);
        int maxSectionY = SectionUtil.maxSectionY(context);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                    long key = SectionUtil.key(chunkX, sectionY, chunkZ);
                    if (!seen.add(key)) {
                        continue;
                    }
                    final int fx = chunkX;
                    final int fy = sectionY;
                    final int fz = chunkZ;
                    executor.submit(() -> {
                        SectionBlob blob = SectionGenerator.generate(context, fx, fy, fz, mode);
                        store.write(blob);
                    });
                }
            }
        }
    }
}
