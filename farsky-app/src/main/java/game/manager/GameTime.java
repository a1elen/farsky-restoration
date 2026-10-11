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
      GameState state = Main.getGameState();
      // Menus no longer pause the world: play time and the day/night cycle keep
      // running while the map, inventory or pause menu are open.
      boolean worldRunning = state == GameState.PLAYING
         || state == GameState.MAP
         || state == GameState.INVENTORY
         || state == GameState.PAUSED;
      if (worldRunning) {
         totalPlayTime += delta;
         updateDayCycle(delta);
      }

      elapsedMillis = (float)(System.currentTimeMillis() - startMillis);
      if (elapsedMillis > 1000000.0F) {
         startMillis += 1000000L;
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

   /** Length of one full day + night cycle in seconds. */
   public static float getCycleDuration() {
      float total = (dayDuration + nightDuration) * 60.0F;
      return total > 0.0F ? total : 1.0F;
   }

   /** 1 based day counter for the HUD ("Day 3 ..."). */
   public static int getDayNumber() {
      return (int)(dayTime / getCycleDuration()) + 1;
   }

   /**
    * In-game clock derived from the day/night cycle: the day phase maps to
    * 06:00 - 18:00 and the night phase to 18:00 - 06:00, so the sun is up in
    * the middle of the "day" portion exactly like {@link #getSunElevation()}.
    */
   public static String getClockLabel() {
      float total = getCycleDuration();
      float t = dayTime % total;
      if (t < 0.0F) {
         t += total;
      }

      float daySeconds = dayDuration * 60.0F;
      float hour;
      if (daySeconds > 0.0F && t < daySeconds) {
         hour = 6.0F + 12.0F * (t / daySeconds);
      } else {
         float nightSeconds = total - daySeconds;
         hour = nightSeconds > 0.0F ? 18.0F + 12.0F * ((t - daySeconds) / nightSeconds) : 18.0F;
      }

      hour = hour % 24.0F;
      int wholeHours = (int)hour;
      int minutes = (int)((hour - wholeHours) * 60.0F);
      if (minutes > 59) {
         minutes = 59;
      }

      return (wholeHours < 10 ? "0" : "") + wholeHours + ":" + (minutes < 10 ? "0" : "") + minutes;
   }

   /** Phase of the current cycle for the HUD: Day, Dusk or Night. */
   public static String getPhaseLabel() {
      if (isNight()) {
         return "Night";
      }

      return isDusk() ? "Dusk" : "Day";
   }
}
