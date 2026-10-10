package game.manager;

import game.Main;

public final class GameTime {
   public static float elapsedMillis = 0.0F;
   public static float totalPlayTime = 0.0F;
   public static float dayTime = 0.0F;
   private static long startMillis = 0L;
   private static float lightLevel = 0.0F;
   private static float dayDuration;
   private static float nightDuration;

   public static void init() {
      startMillis = System.currentTimeMillis();
      lightLevel = 1.0F;
   }

   public static long getStartMillis() {
      return startMillis;
   }

   public static void setTime(float playTime, float time) {
      totalPlayTime = playTime;
      dayTime = time;
   }

   public static void setDayCycle(float dayDur, float nightDur) {
      if (dayDur == 0.0F && nightDur == 0.0F) {
         dayDur = 10.0F;
         nightDur = 7.0F;
      }

      dayDuration = dayDur;
      nightDuration = nightDur;
      updateDayCycle(80.0F);
   }

   /**
    * Multiplayer: snaps this client's clock, play time and light level to the
    * host's authoritative values.
    */
   public static void sync(float playTime, float time, float light) {
      totalPlayTime = playTime;
      dayTime = time;
      lightLevel = Math.max(0.3F, Math.min(1.0F, light));
   }

   public static void update(float delta) {
      if (Main.getGameState() != GameState.PAUSED) {
         totalPlayTime += delta;
      }

      elapsedMillis = (float)(System.currentTimeMillis() - startMillis);
      if (elapsedMillis > 1000000.0F) {
         startMillis += 1000000L;
      }

      if (Main.getGameState() == GameState.PLAYING) {
         updateDayCycle(delta);
      }
   }

   private static void updateDayCycle(float delta) {
      if ((dayTime += delta) % ((dayDuration + nightDuration) * 60.0F) < dayDuration * 60.0F) {
         if (lightLevel < 1.0F) {
            lightLevel += delta / 80.0F;
         } else {
            lightLevel = 1.0F;
         }
      } else if (lightLevel > 0.3F) {
         lightLevel -= delta / 80.0F;
      } else {
         lightLevel = 0.3F;
      }
   }

   public static float getLightLevel() {
      return lightLevel;
   }

   /**
    * Sun elevation above the horizon in degrees for the current time of day.
    * Low in the morning / evening, highest around noon. Used by the shadow map
    * so shadows lengthen and shift through the day instead of staying fixed.
    */
   public static float getSunElevation() {
      if (dayDuration <= 0.0F) {
         return 80.0F;
      }

      float total = (dayDuration + nightDuration) * 60.0F;
      if (total <= 0.0F) {
         return 80.0F;
      }

      float t = dayTime % total;
      if (t < 0.0F) {
         t += total;
      }

      float dayMinutes = dayDuration * 60.0F;
      if (t >= dayMinutes) {
         return 8.0F;
      }

      float dayFrac = t / dayMinutes;
      return 8.0F + 72.0F * (float)Math.sin(dayFrac * Math.PI);
   }

   public static boolean isNight() {
      return lightLevel < 0.5F;
   }

   public static boolean isDusk() {
      return !isNight() && lightLevel < 1.0F;
   }
}
