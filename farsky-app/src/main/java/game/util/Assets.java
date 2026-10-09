package game.util;

import java.io.InputStream;
import java.net.URL;
import org.newdawn.slick.util.ResourceLoader;

/**
 * Classpath lookups for game assets.
 *
 * <p>The restoration build keeps assets at the classpath root ({@code textures/},
 * {@code sounds/}, {@code obj/}), while the original FarSky jar stores the very
 * same files under {@code res/}. Both layouts are supported here so the new
 * jars can run next to (or on top of) a purchased copy of the game without
 * redistributing any of its assets.
 */
public final class Assets {
   private Assets() {
   }

   /** URL of the asset in either layout, or null when it exists nowhere. */
   public static URL getUrl(String path) {
      URL url = Assets.class.getResource("/" + path);
      if (url == null) {
         url = Assets.class.getResource("/res/" + path);
      }

      return url;
   }

   /** Stream over the asset in either layout, or null when it exists nowhere. */
   public static InputStream openStream(String path) {
      try {
         InputStream stream = ResourceLoader.getResourceAsStream(path);
         if (stream != null) {
            return stream;
         }
      } catch (Throwable ignored) {
         // Slick throws when the resource is missing; fall through to plain lookups.
      }

      ClassLoader loader = Assets.class.getClassLoader();
      InputStream stream = loader.getResourceAsStream(path);
      if (stream == null) {
         stream = loader.getResourceAsStream("res/" + path);
      }

      return stream;
   }
}
