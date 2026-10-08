package game.util;

import java.util.Random;

/**
 * Deterministic random source used while a chunk is being generated.
 *
 * <p>The world thread activates a per-chunk stream with {@link #begin(long)} before
 * decorations are placed, so every peer sharing the same world seed builds
 * identical chunks. The active stream is stored per thread: game thread code that
 * runs while a chunk is being built keeps using {@link Math#random()} and can
 * never pollute the deterministic stream.</p>
 *
 * <p>Outside of a {@link #begin(long)} / {@link #end()} section, and on any other
 * thread, all calls fall back to {@link Math#random()}, so replacing
 * {@code Math.random()} with {@link #random()} is always behaviour preserving.</p>
 */
public final class ChunkRandom {
   private static final ThreadLocal<Random> current = new ThreadLocal<>();

   private ChunkRandom() {
   }

   public static void begin(long seed) {
      current.set(new Random(seed));
   }

   public static void end() {
      current.remove();
   }

   public static double random() {
      Random r = current.get();
      return r != null ? r.nextDouble() : Math.random();
   }

   public static float nextFloat() {
      Random r = current.get();
      return r != null ? r.nextFloat() : (float)Math.random();
   }

   /**
    * Mixes the world seed with chunk coordinates into a decoration seed.
    * The same inputs always produce the same seed on every machine.
    */
   public static long chunkSeed(int worldSeed, int chunkX, int chunkZ) {
      long h = worldSeed * 0x9E3779B97F4A7C15L + 0x165667B19E3779F9L;
      h ^= (long)chunkX * 0xBF58476D1CE4E5B9L;
      h = Long.rotateLeft(h, 31) * 0x94D049BB133111EBL;
      h ^= (long)chunkZ * 0x94D049BB133111EBL;
      h = Long.rotateLeft(h, 29) * 0xBF58476D1CE4E5B9L;
      return h ^ (h >>> 32);
   }
}
