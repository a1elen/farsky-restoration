package game.environment.life;

import game.chunks.ChunkManager;
import game.collision.AABB;
import game.collision.CollisionBox;
import game.collision.CollisionDetector;
import game.environment.BloodParticles;
import game.environment.EnvironmentManager;
import game.environment.pickup.ItemPickup;
import game.manager.Camera;
import game.manager.GameScene;
import game.player.damage.Damage;
import game.util.Coord;
import game.util.Point;
import game.util.Segment;

import java.util.ArrayList;

public abstract class Fish {
   public static enum MovingState {
      ROAMING,
      SWIMMING,
      HEIGHT_LOCKED;
   }

   public float roamingRadius = 100.0F;
   protected Point position;
   protected Point prevDirection;
   protected Point rotation = new Point();
   private Point velocity;
   protected Coord direction;
   protected float speed;
   protected float targetSpeed;
   protected float heightOffset;
   protected float health = 1.0F;
   protected AABB boundingBox;
   protected FishType fishType;
   protected MovingState movingState = MovingState.SWIMMING;
   protected float lockedHeight = -700.0F;
   protected Coord spawnCenter;

   // ------------------------------------------------------------------
   // Multiplayer: the host owns every fish, the client only mirrors.
   // ------------------------------------------------------------------

   /** Stable id handed out by the host; -1 while the fish is not networked. */
   private int netId = -1;
   /** True on the client: schooling AI is skipped, state comes from the host. */
   protected boolean remoteControlled = false;
   private Point netTargetPos = null;
   private float netTargetRotX = 0.0F;
   private float netTargetRotY = 0.0F;
   private boolean netRotValid = false;
   private boolean netSnapPending = false;

   public final int getNetId() {
      return this.netId;
   }

   public final void setNetId(int id) {
      this.netId = id;
   }

   public final boolean isRemoteControlled() {
      return this.remoteControlled;
   }

   /** Marks this fish as host driven: its schooling AI never runs locally. */
   public final void beginRemoteControl() {
      this.remoteControlled = true;
   }

   /** Applies one authoritative sample from the host: position and facing. */
   public final void applyNetState(float px, float py, float pz, float rotX, float rotY) {
      Point newPos = new Point(px, py, pz);
      if (this.netTargetPos == null || this.position == null || this.position.distanceTo(newPos) > 400.0F) {
         this.netSnapPending = true;
      }

      this.netTargetPos = newPos;
      this.netTargetRotX = rotX;
      this.netTargetRotY = rotY;
      this.netRotValid = true;
      if (this.prevDirection == null) {
         this.prevDirection = new Point(0.0F, 0.0F, 1.0F);
      }
   }

   /** Runs local schooling AI, or the mirrored transform when the host owns this fish. */
   public final void tick(float delta) {
      if (this.remoteControlled) {
         this.netTick(delta);
      } else {
         this.update(delta);
      }
   }

   /** Client: follows the host transform and keeps the model facing its swim direction. */
   private void netTick(float delta) {
      if (this.netTargetPos == null) {
         return;
      }

      float k = (float)(1.0 - Math.exp(-8.0 * (double)delta));
      Point prev = this.position.copy();
      if (this.netSnapPending) {
         this.position.set(this.netTargetPos);
         this.netSnapPending = false;
      } else {
         this.position.x += (this.netTargetPos.x - this.position.x) * k;
         this.position.y += (this.netTargetPos.y - this.position.y) * k;
         this.position.z += (this.netTargetPos.z - this.position.z) * k;
      }

      this.velocity = this.position.minus(prev);

      if (this.netRotValid) {
         float yaw = this.netTargetRotY - this.rotation.y;
         while (yaw > 180.0F) {
            yaw -= 360.0F;
         }

         while (yaw < -180.0F) {
            yaw += 360.0F;
         }

         this.rotation.x += (this.netTargetRotX - this.rotation.x) * k;
         this.rotation.y += yaw * k;
      }

      this.onUpdate(delta);
   }

   /**
    * Host side: applies a hit the client reported. The synthetic segment runs
    * through the fish so the regular {@link #checkHit} logic (blood, death,
    * loot, stats) resolves exactly as it would for a local swing.
    */
   public final Damage applyRemoteDamage(float amount) {
      if (amount <= 0.0F) {
         return new Damage();
      }

      ArrayList<Segment> segments = new ArrayList<>();
      segments.add(new Segment(this.position.minus(1.0F, 1.0F, 1.0F), this.position.plus(1.0F, 1.0F, 1.0F)));
      return this.checkHit(segments, new Damage(amount, game.player.damage.DamageType.NORMAL));
   }

   public final void update(float delta) {
      if (this.speed < this.targetSpeed) {
         this.speed = this.speed + Math.min(delta * 10.0F, this.targetSpeed - this.speed);
      } else if (this.speed > this.targetSpeed) {
         this.speed = this.speed - Math.min(delta * 10.0F, this.speed - this.targetSpeed);
      }

      switch (this.movingState) {
         case ROAMING:
            if (this.position.toCoord().distanceTo(this.spawnCenter) > this.roamingRadius) {
               Coord target = this.spawnCenter.plus(new Coord(((float)Math.random() - 0.5F) * 2.0F * this.roamingRadius, ((float)Math.random() - 0.5F) * 2.0F * this.roamingRadius));
               this.direction = target.minus(this.position.toCoord());
               this.direction.normalize();
            }
         case SWIMMING:
         case HEIGHT_LOCKED:
         default:
            Point nextPos = this.position.plus(new Point(this.direction.x * this.speed * delta, 0.0F, this.direction.y * this.speed * delta));
            nextPos.y = ChunkManager.getHeight(this.position.x, this.position.z) + this.heightOffset;
            CollisionBox collisionBox = GameScene.getCollisionBoxAt(this.position);
            if (collisionBox != null) {
               this.fleeFrom(collisionBox.getPosition(), false);
            }

            if (this.movingState == MovingState.HEIGHT_LOCKED) {
               nextPos.y = this.lockedHeight;
            }

            Point moveDir = nextPos.minus(this.position);
            moveDir.normalize();
            if (this.prevDirection != null) {
               moveDir = moveDir.scaled(0.03F).plus(this.prevDirection.scaled(0.93F));
            }

            this.position.add(moveDir.scaled(this.speed * delta));
            this.velocity = moveDir.scaled(this.speed * delta);
            if (this.prevDirection != null) {
               this.updateRotation();
            }

            this.prevDirection = moveDir.copy();
            this.onUpdate(delta);
      }
   }

   public static void setupDraw(FishType fishType) {
      if (fishType.isAbyss()) {
         AbyssalFish.setupDraw(fishType);
      } else {
         StandardFish.setupDraw(fishType);
      }
   }

   public abstract void draw();

   public abstract void onUpdate(float delta);

   public abstract ArrayList<ItemPickup> getDrops();

   protected abstract void updateRotation();

   protected abstract BloodParticles.BloodType getBloodType();

   public final AABB getWorldBoundingBox() {
      AABB box = new AABB();
      box.copyFrom(this.boundingBox);
      box.rotate(this.rotation.y);
      box.translate(this.position);
      return box;
   }

   public final Point getVelocity() {
      return this.velocity == null ? new Point() : this.velocity;
   }

   public final boolean shouldRemove() {
      if (this.remoteControlled) {
         // Mirrored fish are dropped by the host's despawn message, not by this
         // side's camera: they may well belong around the other player.
         return false;
      }

      if (this.health <= 0.0F) {
         return true;
      }

      Coord here = new Coord(this.position.x, this.position.z);
      Coord localCam = new Coord(Camera.getPosition().x, Camera.getPosition().z);
      float limit = ChunkManager.viewDistance * 1.2F;
      if (here.distanceTo(localCam) <= limit) {
         return false;
      }

      // Keep fish that only the joined player can see: they are synced to them.
      Point peer = game.net.NetSession.getRemoteAggroPos();
      return peer == null || here.distanceTo(new Coord(peer.x, peer.z)) > limit;
   }

   public final void fleeFrom(Point source, boolean faster) {
      if (this.movingState != MovingState.HEIGHT_LOCKED) {
         this.movingState = MovingState.SWIMMING;
         if (faster) {
            this.targetSpeed *= 2.0F;
         }

         this.direction = new Coord(this.position.x - source.x, this.position.z - source.z);
         this.direction.normalize();
      }
   }

   public final Damage checkHit(ArrayList<Segment> segments, Damage weaponDamage) {
      boolean hit = false;
      Damage damage = new Damage();

      for (int i = 0; i < segments.size(); i++) {
         if (CollisionDetector.segmentIntersects(segments.get(i), this.boundingBox, this.position, this.rotation)) {
            hit = true;
            damage.setSource(segments.get(i).start);
         }
      }

      if (hit) {
         EnvironmentManager.addBloodParticles(new BloodParticles(damage.getSource(), 7, this.getBloodType()));
         damage.accumulate(weaponDamage);
         // Mirrored copies leave health alone: the host applies the hit.
         if (!this.remoteControlled) {
            this.health = this.health - weaponDamage.getAmount();
            if (this.health < 0.0F) {
               this.health = 0.0F;
               GameScene.stats.recordFishKilled();
               EnvironmentManager.addItemPickups(this.getDrops());
            }
         }
      }

      return damage;
   }

   public FishType getFishType() {
      return this.fishType;
   }

   /** Live position, read by the host's state broadcast. */
   public final Point getPos() {
      return this.position;
   }

   /** Live model rotation (pitch/yaw in degrees), read by the state broadcast. */
   public final Point getRotation() {
      return this.rotation;
   }
}
