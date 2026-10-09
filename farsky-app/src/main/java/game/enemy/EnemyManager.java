package game.enemy;

import game.chunks.ChunkManager;
import game.enemy.enemyWithMouth.Anglerfish;
import game.enemy.enemyWithMouth.Barracuda;
import game.enemy.enemyWithMouth.FrilledShark;
import game.enemy.enemyWithMouth.Shark;
import game.enemy.jellyFish.JellyFish;
import game.enemy.kraken.Kraken;
import game.enemy.lightning.AbyssalLightningFish;
import game.environment.DepthAtmosphere;
import game.manager.Camera;
import game.manager.GameScene;
import game.player.damage.Damage;
import game.util.Coord;
import game.util.Point;
import game.util.Segment;
import game.util.State;
import java.util.ArrayList;
import org.lwjgl.opengl.GL11;

public final class EnemyManager {
   public boolean bossPresent;
   private ArrayList<Enemy> enemies;
   private float despawnDist = DepthAtmosphere.getMaxFogDistance() + 400.0F;
   /** Host: monotonically increasing creature id, never reused inside a session. */
   private static int nextNetId = 1;

   public EnemyManager() {
      this.bossPresent = false;
      this.enemies = new ArrayList<>();
   }

   public final void tick(float deltaTime) {
      // The client never spawns or reacts to alerts: the host owns the AI.
      if (!game.net.NetSession.isClientMirror()) {
         GameScene.enemyGenerator.tick(deltaTime);
      }

      if (GameScene.avatar != null) {
         this.bossPresent = false;

         for (int i = 0; i < this.enemies.size(); i++) {
            if (this.enemies.get(i).getType().isBoss()) {
               this.bossPresent = true;
               break;
            }
         }
      }

      for (int i = 0; i < this.enemies.size(); i++) {
         this.enemies.get(i).tick(deltaTime);
      }

      if (GameScene.avatar != null) {
         for (int i = this.enemies.size() - 1; i >= 0; i--) {
            Enemy enemy = this.enemies.get(i);
            // Mirrored creatures live or die by the host's despawn messages,
            // not by this side's camera.
            if (!enemy.isRemoteControlled() && this.distanceToNearestPlayer(enemy.getPosition()) > this.despawnDist) {
               this.removeAt(i);
            } else if (enemy.isReadyForRemoval()) {
               this.removeAt(i);
            }
         }
      }
   }

   private void removeAt(int index) {
      Enemy enemy = this.enemies.get(index);
      if (enemy.getNetId() >= 0) {
         game.net.NetSession.sendEnemyDespawn(enemy.getNetId());
      }

      enemy.onRemove();
      this.enemies.remove(index);
   }

   /** Distance from a point to whichever player (local or peer) is closer. */
   private float distanceToNearestPlayer(Point point) {
      if (point == null) {
         return 0.0F;
      }

      float distance = point.distanceTo(Camera.getPosition());
      game.net.RemotePlayer remote = game.net.NetSession.getRemotePlayer();
      if (remote != null) {
         distance = Math.min(distance, point.distanceTo(remote.getRenderPos()));
      }

      return distance;
   }

   public final void draw() {
      EnemyType lastType = null;
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

      for (int i = 0; i < this.enemies.size(); i++) {
         if (lastType == null || this.enemies.get(i).getType() != lastType) {
            if (i > 0) {
               this.enemies.get(i - 1).cleanupRender();
            }

            this.enemies.get(i).setupRender();
            lastType = this.enemies.get(i).getType();
         }

         this.enemies.get(i).drawBody();
      }

      if (this.enemies.size() > 0) {
         this.enemies.get(this.enemies.size() - 1).cleanupRender();
      }
   }

   public final void drawExtra() {
      EnemyType lastType = null;
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

      for (int i = 0; i < this.enemies.size(); i++) {
         if (lastType == null || this.enemies.get(i).getType() != lastType) {
            if (i > 0) {
               this.enemies.get(i - 1).cleanupExtraRender();
            }

            this.enemies.get(i).setupExtraRender();
            lastType = this.enemies.get(i).getType();
         }

         this.enemies.get(i).drawExtra();
      }

      if (this.enemies.size() > 0) {
         this.enemies.get(this.enemies.size() - 1).cleanupExtraRender();
      }
   }

   public final synchronized void add(Enemy enemy) {
      if (enemy.getNetId() < 0) {
         enemy.setNetId(nextNetId++);
      }

      this.enemies.add(enemy);
   }

   public final Enemy spawn(Coord coord, EnemyType type) {
      Point spawnPos = new Point(coord.x, 0.0F, coord.y);
      spawnPos.y = ChunkManager.getHeight((int)spawnPos.x, (int)spawnPos.z);
      Enemy enemy = this.createEnemy(spawnPos, type);
      if (enemy == null) {
         return null;
      }

      enemy.setNetId(nextNetId++);
      this.enemies.add(enemy);

      if (type.isBoss()) {
         this.bossPresent = true;
      }

      return enemy;
   }

   /**
    * Client: builds a creature from a host state sample. It never runs AI, all
    * further state arrives through {@link Enemy#applyNetState}.
    */
   public final Enemy spawnRemote(int id, Point pos, EnemyType type) {
      Enemy enemy = this.createEnemy(pos.copy(), type);
      if (enemy == null) {
         return null;
      }

      enemy.setNetId(id);
      enemy.beginRemoteControl();
      this.enemies.add(enemy);
      if (id >= nextNetId) {
         nextNetId = id + 1;
      }

      if (type.isBoss()) {
         this.bossPresent = true;
      }

      return enemy;
   }

   private Enemy createEnemy(Point spawnPos, EnemyType type) {
      switch (type) {
         case JELLYFISH:
            return new JellyFish(spawnPos);
         case SHARK:
         case HAMMERHEAD:
         case GREAT_WHITE:
            return new Shark(spawnPos, type);
         case BARRACUDA:
            return new Barracuda(spawnPos);
         case ANGLERFISH:
            return new Anglerfish(spawnPos);
         case FRILLED_SHARK:
            return new FrilledShark(spawnPos);
         case DEEP_SEA_FISH:
            return new AbyssalLightningFish(spawnPos);
         case KRAKEN:
            return new Kraken(spawnPos, -1, -1, false);
         default:
            return null;
      }
   }

   /** Client: drops a creature the host removed (by explicit despawn message). */
   public final void removeRemote(int id) {
      for (int i = this.enemies.size() - 1; i >= 0; i--) {
         if (this.enemies.get(i).getNetId() == id) {
            this.removeAt(i);
         }
      }
   }

   /** The creature carrying this host id, or null when it is not in the world. */
   public final Enemy getByNetId(int id) {
      for (int i = 0; i < this.enemies.size(); i++) {
         if (this.enemies.get(i).getNetId() == id) {
            return this.enemies.get(i);
         }
      }

      return null;
   }

   public final void removeAll() {
      for (int i = this.enemies.size() - 1; i >= 0; i--) {
         this.removeAt(i);
      }
   }

   public final State resolveCollision(State prevState, State curState) {
      return curState;
   }

   public final Damage applyHit(Segment segment, Damage damage) {
      ArrayList<Segment> segments = new ArrayList<>();
      segments.add(segment);
      return this.applyHitToAll(segments, damage);
   }

   private Damage applyHitToAll(ArrayList<Segment> segments, Damage damage) {
      Damage result = new Damage();

      for (int i = 0; i < this.enemies.size(); i++) {
         result.accumulate(this.enemies.get(i).applyHit(segments, damage));
      }

      return result;
   }

   public final float getNearestBossDistance(Coord from) {
      float minDist = 9999999.0F;

      for (int i = 0; i < this.enemies.size(); i++) {
         float dist = this.enemies.get(i).getPosition().toCoord().distanceTo(from);
         if (this.enemies.get(i).getType().isBoss() && dist < minDist) {
            minDist = dist;
         }
      }

      return minDist;
   }

   public final ArrayList<Enemy> getEnemies() {
      return this.enemies;
   }

   public final int countAtChunk(int chunkX, int chunkZ) {
      int count = 0;

      for (int i = 0; i < this.enemies.size(); i++) {
         if (this.enemies.get(i).isAtChunk(chunkX, chunkZ)) {
            count++;
         }
      }

      return count;
   }
}
