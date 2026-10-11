package game.enemy;

import game.manager.InGameState;
import game.manager.Loading;
import game.chunks.ChunkManager;
import game.environment.DepthAtmosphere;
import game.manager.Camera;
import game.manager.GameScene;
import game.manager.GameTime;
import game.util.Coord;
import game.util.Point;

public final class EnemyGenerator {
   public static enum SpawningLevel {
      NEVER,
      LOW,
      NORMAL,
      HIGH;
   }

   private static float spawnInterval;
   private Point alertTarget = null;
   private float alertCooldown;
   private SpawningLevel spawningLevel;
   private float spawnAccum;

   public EnemyGenerator(SpawningLevel level) {
      spawnInterval = 0.0F;
      this.spawnAccum = spawnInterval;
      this.alertCooldown = 100.0F;
      this.spawningLevel = level;
      if (this.spawningLevel == null) {
         this.spawningLevel = SpawningLevel.NORMAL;
      }
   }

   public final void tick(float deltaTime) {
      if (GameScene.getInGameState() != InGameState.INITIALIZING) {
         this.spawnAccum += deltaTime;
      }

      this.alertCooldown += deltaTime;
      if (GameTime.isNight()) {
         spawnInterval = DepthAtmosphere.getNightSpawnInterval();
      } else {
         spawnInterval = DepthAtmosphere.getDaySpawnInterval();
      }

      switch (this.spawningLevel) {
         case NEVER:
            this.spawnAccum = 0.0F;
            break;
         case LOW:
            spawnInterval *= 1.2F;
         case NORMAL:
         default:
            break;
         case HIGH:
            spawnInterval *= 0.8F;
      }

      if (this.spawnAccum >= spawnInterval) {
         this.spawnAccum = this.spawnAccum - spawnInterval;
         if (Math.random() < 0.8F) {
            this.spawnForPlayers();
         }
      }

      if (this.alertTarget != null) {
         boolean alerted = false;

         for (int i = 0; i < GameScene.enemyManager.getEnemies().size(); i++) {
            if (!GameScene.enemyManager.getEnemies().get(i).getType().isBoss() && Math.random() < -(1F / 700F) * GameScene.enemyManager.getEnemies().get(i).getPosition().distanceTo(this.alertTarget) + (12F / 7F) && this.alertCooldown >= 100.0F) {
               GameScene.enemyManager.getEnemies().get(i).setTarget(this.alertTarget);
               alerted = true;
            }
         }

         if (alerted) {
            this.alertCooldown = 0.0F;
         }

         this.alertTarget = null;
      }
   }

   /**
    * Lays the spawn ring around every player in the session, so the host does
    * not end up being the only one with creatures around them.
    */
   private void spawnForPlayers() {
      float startAngle = GameScene.avatar.getLookDir().toCoord().angle();
      this.spawnRing(Camera.getPosition().x, Camera.getPosition().z, GameScene.avatar.getPos2D(), startAngle);

      java.util.ArrayList<game.net.RemotePlayer> remotes = game.net.NetSession.getRemotePlayers();
      Coord localCenter = new Coord(Camera.getPosition().x, Camera.getPosition().z);
      for (int i = 0; i < remotes.size(); i++) {
         game.net.RemotePlayer remote = remotes.get(i);
         Point remotePos = remote.getRenderPos();
         Coord remoteCenter = remotePos.toCoord();
         // Players swimming together share one ring instead of doubling up.
         if (remoteCenter.distanceTo(localCenter) <= ChunkManager.viewDistance * 2.0F) {
            continue;
         }

         boolean covered = false;
         for (int j = 0; j < i; j++) {
            if (remoteCenter.distanceTo(remotes.get(j).getRenderPos().toCoord()) <= ChunkManager.viewDistance * 2.0F) {
               covered = true;
               break;
            }
         }

         if (covered) {
            continue;
         }

         float yawRad = (float)Math.toRadians((double)remote.getRenderYaw());
         Coord remoteLook = new Coord(-Math.sin((double)yawRad), -Math.cos((double)yawRad));
         this.spawnRing(remotePos.x, remotePos.z, remoteCenter, remoteLook.angle());
      }
   }

   /** One spawn ring at view distance around (ringX, ringZ), starting at startAngle. */
   private void spawnRing(float ringX, float ringZ, Coord stageRef, float startAngle) {
      for (float theta = 0.0F; theta <= Math.PI * 2; theta = (float)(theta + (Math.PI / 10))) {
         Coord spawnCoord = new Coord(ringX, ringZ)
            .plus(new Coord(ChunkManager.viewDistance * Math.cos(startAngle + theta), ChunkManager.viewDistance * Math.sin(startAngle + theta)));
         EnemyType enemyType = null;
         if (Loading.worldManager.getStageAt(spawnCoord.x, spawnCoord.y) == Loading.worldManager.getStageAt(stageRef.x, stageRef.y)) {
            enemyType = selectEnemyType(Loading.worldManager.getStageAt(spawnCoord.x, spawnCoord.y));
         }

         if (enemyType != null) {
            if (enemyType == EnemyType.JELLYFISH) {
               for (int j = 0; j < 12; j++) {
                  GameScene.enemyManager.spawn(spawnCoord, enemyType);
               }
            } else if (enemyType == EnemyType.BARRACUDA && GameTime.isNight()) {
               for (int j = 0; j < 3; j++) {
                  GameScene.enemyManager.spawn(spawnCoord, enemyType);
               }
            } else {
               GameScene.enemyManager.spawn(spawnCoord, enemyType);
            }
            break;
         }
      }
   }

   private static EnemyType selectEnemyType(int stage) {
      switch (stage) {
         case 0:
            if ((int)(Math.random() * 15.0) == 0) return EnemyType.GREAT_WHITE;
            switch ((int)(Math.random() * 3.0)) {
               case 0: return EnemyType.SHARK;
               case 1: return EnemyType.HAMMERHEAD;
               default: return EnemyType.BARRACUDA;
            }
         case 1:
            switch ((int)(Math.random() * 3.0)) {
               case 0: return EnemyType.JELLYFISH;
               case 1: return EnemyType.ANGLERFISH;
               default: return EnemyType.FRILLED_SHARK;
            }
         case 2:
            switch ((int)(Math.random() * 13.0)) {
               case 0: return EnemyType.DEEP_SEA_FISH;
               case 1: return EnemyType.JELLYFISH;
               default: return EnemyType.KRAKEN;
            }
         default:
            return null;
      }
   }

   public final void alert(Point target) {
      this.alertTarget = target;
   }
}
