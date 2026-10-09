package game.enemy;

import game.collision.AABB;
import game.collision.CollisionDetector;
import game.enemy.AI.EnemyNavigator;
import game.environment.DeathFragment;
import game.environment.EnvironmentManager;
import game.manager.GameScene;
import game.player.damage.Damage;
import game.player.damage.DamageType;
import game.util.Point;
import game.util.Segment;

import java.util.ArrayList;

public abstract class Enemy {
   /** Distance (in units) beyond which a remote transform snaps instead of sliding. */
   private static final float NET_SNAP_DISTANCE = 250.0F;
   /** Exponential interpolation response, same curve as RemotePlayer. */
   private static final float NET_SMOOTHING = 15.0F;

   protected EnemyType type;
   protected float health;
   protected float maxHealth;
   protected Point position;
   protected boolean aggressive = false;
   protected float hitFlash = 0.0F;
   protected boolean dead = false;
   private float deathTimer;
   private boolean readyForRemoval = false;

   // ------------------------------------------------------------------
   // Multiplayer: the host owns every creature, the client only mirrors.
   // ------------------------------------------------------------------

   /** Stable id handed out by the host; -1 while the creature is not networked. */
   private int netId = -1;
   /** True on the client: local AI is skipped, transforms come from the host. */
   protected boolean remoteControlled = false;
   /** Latest authoritative transform received from the host. */
   private Point netTargetPos = null;
   private Point netTargetDir = null;
   private boolean netSnapPending = false;
   private float netMouthValue = 0.0F;

   public abstract void update(float deltaTime);

   public abstract void setupRender();

   public abstract void renderBody();

   public abstract void cleanupRender();

   public abstract void setupExtraRender();

   public abstract void renderExtra();

   public abstract void cleanupExtraRender();

   public abstract void setTarget(Point target);

   public abstract void onRemove();

   public abstract Damage checkHit(ArrayList<Segment> segments, Damage damage);

   public abstract boolean isAtChunk(int chunkX, int chunkZ);

   public final Damage applyHit(ArrayList<Segment> segments, Damage damage) {
      if (this.dead) {
         return new Damage();
      }

      Damage result = this.checkHit(segments, damage);
      // On the client the local copy only produces blood/flash feedback: the
      // host is told about the hit and applies the real damage itself.
      if (this.remoteControlled && result.getAmount() > 0.0F) {
         game.net.NetSession.sendEnemyHit(this.netId, damage.getAmount());
      }

      return result;
   }

   public final void tick(float deltaTime) {
      if (this.remoteControlled) {
         this.remoteTick(deltaTime);
         return;
      }

      if (this.dead || GameScene.avatar != null && GameScene.avatar.isInside()
            && this.focusIsLocalAvatar() && !this.type.isBoss()) {
         this.aggressive = false;
      }

      if (this.dead) {
         if (this.deathTimer <= 0.0F) {
            this.readyForRemoval = true;
            this.deathTimer = 0.0F;
         } else {
            this.deathTimer -= deltaTime;
         }
      } else {
         this.update(deltaTime);
      }
   }

   // ------------------------------------------------------------------
   // Remote (client) simulation: no AI, just interpolation + animation.
   // ------------------------------------------------------------------

   /**
    * Advances a mirrored creature. Subclasses override {@link #remoteTransform}
    * to feed their own renderer (navigator, jaw mesh, ...).
    */
   protected void remoteTick(float deltaTime) {
      if (this.dead) {
         if (this.deathTimer <= 0.0F) {
            this.readyForRemoval = true;
            this.deathTimer = 0.0F;
         } else {
            this.deathTimer -= deltaTime;
         }

         return;
      }

      this.remoteTransform(deltaTime);
      this.remoteAfterTransform(deltaTime);
      if (this.hitFlash > 0.0F) {
         this.hitFlash -= deltaTime * 5.0F;
         if (this.hitFlash < 0.0F) {
            this.hitFlash = 0.0F;
         }
      }
   }

   /** Default transform follower: drives {@link #position} only. */
   protected void remoteTransform(float deltaTime) {
      if (this.position == null || this.netTargetPos == null) {
         return;
      }

      if (this.netTakeSnap(this.position.distanceTo(this.netTargetPos))) {
         this.position.set(this.netTargetPos);
         return;
      }

      float k = netLerpFactor(deltaTime);
      this.position.x += (this.netTargetPos.x - this.position.x) * k;
      this.position.y += (this.netTargetPos.y - this.position.y) * k;
      this.position.z += (this.netTargetPos.z - this.position.z) * k;
   }

   /** True when the creature should jump to the network transform instead of sliding. */
   protected final boolean netTakeSnap(float distance) {
      if (this.netSnapPending) {
         this.netSnapPending = false;
         return true;
      }

      return distance > NET_SNAP_DISTANCE;
   }

   protected static float netLerpFactor(float deltaTime) {
      return (float)(1.0 - Math.exp((double)(-NET_SMOOTHING * deltaTime)));
   }

   /** Steps the received facing towards the authoritative one; null when nothing was received yet. */
   protected final Point netDirectionToward(Point current, boolean snap, float k) {
      if (this.netTargetDir == null) {
         return null;
      }

      if (snap || current == null) {
         return this.netTargetDir.copy();
      }

      Point next = new Point(
            current.x + (this.netTargetDir.x - current.x) * k,
            current.y + (this.netTargetDir.y - current.y) * k,
            current.z + (this.netTargetDir.z - current.z) * k);
      next.normalize();
      return next;
   }

   /** Last authoritative position, for subclasses driving their own transform. */
   protected final Point netTargetPosition() {
      return this.netTargetPos;
   }

   /** Last authoritative facing vector. */
   protected final Point netTargetFacing() {
      return this.netTargetDir;
   }

   /** Last authoritative jaw opening (0..1). */
   protected final float netMouthTarget() {
      return this.netMouthValue;
   }

   // ------------------------------------------------------------------
   // Network state access (called by game.net.NetSession)
   // ------------------------------------------------------------------

   public final int getNetId() {
      return this.netId;
   }

   public final void setNetId(int id) {
      this.netId = id;
   }

   public final boolean isRemoteControlled() {
      return this.remoteControlled;
   }

   /** Marks this instance as host driven: its AI never runs locally. */
   public final void beginRemoteControl() {
      this.remoteControlled = true;
   }

   /**
    * Applies one authoritative state sample from the host.
    *
    * @param px/py/pz  authoritative position
    * @param dx/dy/dz  authoritative facing vector (unit length)
    * @param health    authoritative hit points
    * @param stateOrd  subclass state ordinal (ai mode), mirrored for rendering
    * @param mouth     jaw opening 0..1
    * @param hitFlash  red damage flash 0..1
    * @param dying     death animation started on the host
    * @param dead      creature already finished on the host
    */
   public final void applyNetState(float px, float py, float pz,
                                   float dx, float dy, float dz,
                                   float health, int stateOrd, float mouth,
                                   float hitFlash, boolean dying, boolean dead) {
      Point newPos = new Point(px, py, pz);
      boolean snap = this.netTargetPos == null
            || this.getPositionSnapshot() == null
            || this.getPositionSnapshot().distanceTo(newPos) > NET_SNAP_DISTANCE;
      this.netTargetPos = newPos;
      this.netTargetDir = new Point(dx, dy, dz);
      if (snap) {
         this.netSnapPending = true;
      }

      this.applyNetHealth(health);
      this.applyNetStateOrdinal(stateOrd);
      this.netMouthValue = mouth;
      this.applyNetMouth(mouth);
      this.hitFlash = hitFlash;
      if (dying) {
         this.applyNetDying();
      }

      if (dead && !this.dead) {
         this.dead = true;
         this.deathTimer = 2.5F;
      }
   }

    /** Position used for the first-sample snap check; JellyFish shadows {@link #position}. */
   protected Point getPositionSnapshot() {
      return this.position;
   }

   /**
    * Host side: applies a hit the client reported. The synthetic segment runs
    * through the creature so the regular {@link #checkHit} logic (blood, death,
    * loot, stats) resolves exactly as it would for a local swing.
    */
   public final Damage applyRemoteDamage(float amount) {
      if (this.dead || amount <= 0.0F) {
         return new Damage();
      }

      Point p = this.getPosition();
      if (p == null) {
         return new Damage();
      }

      ArrayList<Segment> segments = new ArrayList<>();
      segments.add(new Segment(p.minus(1.0F, 1.0F, 1.0F), p.plus(1.0F, 1.0F, 1.0F)));
      return this.checkHit(segments, new Damage(amount, DamageType.NORMAL));
   }

   // -- combat focus: which of the two players this creature hunts ---------

   /** Which player this creature hunts this tick; see {@link #updateCombatFocus()}. */
   protected Point combatFocus;
   /** True while {@link #combatFocus} is this side's own avatar. */
   protected boolean combatFocusIsLocal = true;

   /**
    * Host: picks the player the creature is focused on this tick - the local
    * avatar, or the joined player when they are the closer target. Damage is
    * still resolved per side: the host bites its own avatar, the client bites
    * its own from the mirrored state.
    */
   protected final void updateCombatFocus() {
      Point origin = this.getPosition();
      Point local = GameScene.avatar == null ? null : GameScene.avatar.getCameraPos();
      Point peer = game.net.NetSession.getRemoteAggroPos();

      if (local == null) {
         this.combatFocus = peer;
         this.combatFocusIsLocal = false;
      } else if (peer == null || origin == null) {
         this.combatFocus = local;
         this.combatFocusIsLocal = true;
      } else if (peer.distanceTo(origin) < local.distanceTo(origin)) {
         this.combatFocus = peer;
         this.combatFocusIsLocal = false;
      } else {
         this.combatFocus = local;
         this.combatFocusIsLocal = true;
      }
   }

   /** True when the target selected by {@link #updateCombatFocus()} is our avatar. */
   protected final boolean focusIsLocalAvatar() {
      return this.combatFocusIsLocal;
   }

   /** The player this creature is currently focused on (null when nobody is around). */
   protected final Point getCombatFocus() {
      if (this.combatFocus == null) {
         this.updateCombatFocus();
      }

      return this.combatFocus;
   }

   // -- state hooks: overridden by the concrete creatures ----------------

   protected void applyNetHealth(float value) {
      this.health = value;
    }

   public final float getNetHealthForSync() {
      return this.currentNetHealth();
   }

   /** Host: ai-state ordinal broadcast to the peer (rendering + bite prediction). */
   public final int getNetStateForSync() {
      return this.currentNetStateOrdinal();
   }

   /** Host: jaw opening 0..1 broadcast to the peer. */
   public final float getNetMouthForSync() {
      return this.currentNetMouth();
   }

   /** Host: red damage flash 0..1 broadcast to the peer. */
   public final float getNetHitFlashForSync() {
      return this.hitFlash;
   }

   /**
    * Host: authoritative facing broadcast to the peer. Creatures that steer
    * through an {@link EnemyNavigator} expose it via {@link #netNavigator()}.
    */
   public final Point getNetFacingForSync() {
      EnemyNavigator navigator = this.netNavigator();
      if (navigator != null && navigator.getDirection() != null) {
         return navigator.getDirection();
      }

      return new Point(0.0F, 0.0F, 1.0F);
   }

   /** Subclasses that steer through an {@link EnemyNavigator} expose it here. */
   protected EnemyNavigator netNavigator() {
      return null;
   }

   protected float currentNetHealth() {
      return this.health;
   }

   protected void applyNetStateOrdinal(int ordinal) {
   }

   protected int currentNetStateOrdinal() {
      return 0;
   }

   protected void applyNetMouth(float value) {
   }

   protected float currentNetMouth() {
      return 0.0F;
   }

   /** Host: whether the death animation already runs on this creature. */
   public final boolean isNetDying() {
      return this.currentNetDying();
   }

   protected boolean currentNetDying() {
      return false;
   }

   protected void applyNetDying() {
   }

   public final boolean isNetDead() {
      return this.dead;
   }

   public final void drawBody() {
      if (!this.dead) {
         this.renderBody();
      }
   }

   public void drawExtra() {
      if (!this.dead) {
         this.renderExtra();
      }
   }

   /** Hook for subclasses: runs after the transform was applied on a mirrored creature. */
   protected void remoteAfterTransform(float deltaTime) {
   }

   public Point getPosition() {
      return this.position;
   }

   public final EnemyType getType() {
      return this.type;
   }

   public final boolean isAggressive() {
      return this.aggressive;
   }

   protected static boolean accumulateSegmentHits(ArrayList<Segment> segments, AABB hitbox, Point position, Point rotation, Damage result) {
      boolean hit = false;

      for (int i = 0; i < segments.size(); i++) {
         if (CollisionDetector.segmentIntersects(segments.get(i), hitbox, position, rotation)) {
            hit = true;
            result.setSource(segments.get(i).start);
         }
      }

      return hit;
   }

   protected final void die(ArrayList<Segment> segments) {
      this.dead = true;
      this.deathTimer = 2.5F;
      GameScene.stats.recordPredatorKilled();
      if (segments != null) {
         for (int i = 0; i < segments.size(); i++) {
            for (float t = 0.0F; t < segments.get(i).length(); t += 0.3F) {
               Point segStart = segments.get(i).start;
               Segment seg = segments.get(i);
               Point dir = seg.end.minus(seg.start);
               dir.normalize();
               Point fragPos = segStart.plus(dir.scaled(t));
               EnvironmentManager.addDeathFragment(new DeathFragment(fragPos, 30.0F));
            }
         }
      }
   }

   public final boolean isReadyForRemoval() {
      return this.readyForRemoval;
   }
}
