package game.enemy.enemyWithMouth;

import game.collision.AABB;
import game.collision.CollisionDetector;
import game.enemy.Enemy;
import game.enemy.AI.EnemyNavigator;
import game.enemy.AI.Intelligence;
import game.environment.BloodParticles;
import game.environment.EnvironmentManager;
import game.manager.GameScene;
import game.manager.GameTime;
import game.player.damage.Damage;
import game.shader.Shaders;
import game.render.AnimatedMesh;
import game.sounds.SoundManager;
import game.util.Point;
import game.util.Segment;

import java.util.ArrayList;
import org.lwjgl.opengl.GL11;

public abstract class EnemyWithMouth extends Enemy {
   public static enum State {
      IDLE,
      CHASING,
      ATTACKING;
   }

   protected AnimatedMesh bodyMesh;
   protected AABB hitbox;
   protected State state;
   protected float scale;
   private EnemyNavigator navigator;
   private float attackCooldown;
   private float mouthOpen;
   private boolean attackInterrupted = false;
   protected int soundSource;
   private Point chaseTarget;
   private boolean isTargeted = false;
   private float mouthOscillation = 0.0F;
   private float aggroRange = -1.0F;
   private float nightAggroRange = 180.0F;
   protected float speedMult = 1.0F;
   protected float attackDamage = 10.0F;
   /** Client: local bite cadence while mirroring a host creature that is attacking. */
   private float remoteBiteCooldown = 0.0F;

   public EnemyWithMouth(Point position, float bodySize) {
      this.state = State.IDLE;
      this.position = position.plus(0.0F, bodySize, 0.0F);
      this.navigator = new EnemyNavigator(this.position, bodySize, Intelligence.AGGRESSIVE);
      this.attackCooldown = 6.0F;
      this.mouthOpen = 0.0F;
   }

   protected abstract void onDeath();

   protected void setupBodyRender() {
      if (this.state == State.ATTACKING) {
         Shaders.setUniform("height", 8.0);
         Shaders.setUniform("factor", 15.0);
      } else {
         Shaders.setUniform("height", 9.0);
         Shaders.setUniform("factor", 8.0);
      }
   }

   protected abstract void updateSubclass(float deltaTime);

   @Override
   public final void update(float deltaTime) {
      this.updateCombatFocus();
      this.updateSubclass(deltaTime);
      float damageMult = 0.1F;
      if (this.hitFlash > 0.0F) {
         this.hitFlash -= deltaTime * 5.0F;
      } else {
         this.hitFlash = 0.0F;
      }

      switch (this.state) {
         case IDLE:
            if (this.combatFocus != null && this.combatFocus.distanceTo(this.position) < this.aggroRange) {
               this.aggressive = true;
            }

            if (this.combatFocus != null && GameTime.isNight() && this.combatFocus.distanceTo(this.position) < this.nightAggroRange) {
               this.aggressive = true;
            }

            if (!this.aggressive && !this.isTargeted) {
               this.navigator.navigate(deltaTime, 40.0F * this.speedMult);
            } else {
               this.navigator.navigate(deltaTime, 70.0F * this.speedMult);
            }

            if (!this.dead) {
               SoundManager.setLoopingSourceVolume(this.soundSource, 0.3F);
               SoundManager.setLoopingSourcePitch(this.soundSource, 1.0F);
            }

            if (this.focusIsLocalAvatar() && GameScene.avatar.isInside()) {
               this.attackCooldown = 5.0F;
            }

            if (this.aggressive || this.isTargeted) {
               this.attackCooldown -= deltaTime;
               if (this.attackCooldown <= 0.0F && !(this.focusIsLocalAvatar() && GameScene.avatar.isInside())) {
                  this.state = State.ATTACKING;
                  this.attackCooldown = 4.0F + (float)Math.random() * 2.0F;
                  this.attackInterrupted = false;
               }
            } else if (this.chaseTarget != null) {
               this.state = State.CHASING;
            }

            if (this.mouthOpen > 0.0F) {
               this.mouthOpen -= deltaTime;
            } else {
               this.mouthOpen = 0.0F;
            }
            break;
         case CHASING:
            this.isTargeted = true;
            this.navigator.navigate(deltaTime, 70.0F * this.speedMult);
            this.navigator.trySetTarget(this.chaseTarget);
            if (this.position.distanceTo(this.chaseTarget) < 50.0F) {
               this.attackCooldown = 2.0F + (float)Math.random() * 2.0F;
               this.chaseTarget = null;
               this.state = State.IDLE;
            }
            break;
         case ATTACKING:
            this.aggressive = true;
            damageMult = 1.0F;
            if ((this.focusIsLocalAvatar() && GameScene.avatar.isInside())
                  || this.getCombatFocus() == null
                  || !this.navigator.trySetTarget(this.getCombatFocus())
                  || this.attackInterrupted) {
               this.state = State.IDLE;
               this.attackInterrupted = false;
            }

            this.navigator.navigate(deltaTime, 200.0F * this.speedMult);
            if (!this.dead) {
               SoundManager.setLoopingSourceVolume(this.soundSource, 0.8F);
               SoundManager.setLoopingSourcePitch(this.soundSource, 1.8F);
            }

            if (this.mouthOpen < 1.0F) {
               this.mouthOpen += deltaTime * 3.0F;
            } else {
               this.mouthOpen = 1.0F;
            }
      }

      if (this.mouthOscillation > 0.0F) {
         this.mouthOpen = (float)Math.cos(this.mouthOscillation) * 0.5F + 0.5F;
         this.mouthOscillation -= deltaTime * 25.0F;
      }

      if (CollisionDetector.containsPoint(GameScene.avatar.getCameraPos(), this.hitbox, this.position, this.navigator.getDirection().toAngles())) {
         if (this.state == State.ATTACKING) {
            GameScene.avatar.takeDamage(this.attackDamage * damageMult, "You were killed by " + this.type.getName());
         }

         GameScene.avatar.applyImpulse(new Point(0.0F, 1.0F, 0.0F).scaled(75.0F));
         this.aggressive = true;
         this.state = State.IDLE;
      }

      if (this.navigator.isDyingComplete()) {
         this.health = 0.0F;
         this.onDeath();
         // die(null) marks the corpse dead (deathTimer 2.5 s) so this block
         // runs exactly once; without it the body would drop loot every frame.
         this.die(null);
         this.onRemove();
      }

      this.bodyMesh.blendToFrame(1, this.mouthOpen);
      this.position = this.navigator.getPosition();
      if (!this.dead) {
         SoundManager.setLoopingSourcePosition(this.soundSource, this.position);
      }
   }

   // ------------------------------------------------------------------
   // Host-authoritative mirror (client side): follow the host transform,
   // animate the jaw, and land the bite on this player's avatar.
   // ------------------------------------------------------------------

   @Override
   protected EnemyNavigator netNavigator() {
      return this.navigator;
   }

   @Override
   protected void remoteTransform(float deltaTime) {
      Point target = this.netTargetPosition();
      if (target == null) {
         return;
      }

      float k = netLerpFactor(deltaTime);
      boolean snap = this.netTakeSnap(this.position.distanceTo(target));
      if (snap) {
         this.position.set(target);
      } else {
         this.position.x += (target.x - this.position.x) * k;
         this.position.y += (target.y - this.position.y) * k;
         this.position.z += (target.z - this.position.z) * k;
      }

      this.navigator.setPosition(this.position);
      Point dir = this.netDirectionToward(this.navigator.getDirection(), snap, k);
      if (dir != null) {
         this.navigator.setDirection(dir);
      }

      this.position = this.navigator.getPosition();
      this.navigator.tickDying(deltaTime);

      float mouthTarget = this.netMouthTarget();
      if (snap) {
         this.mouthOpen = mouthTarget;
      } else {
         this.mouthOpen += (mouthTarget - this.mouthOpen) * k;
      }

      this.bodyMesh.blendToFrame(1, this.mouthOpen);
      if (!this.dead) {
         SoundManager.setLoopingSourcePosition(this.soundSource, this.position);
      }
   }

   @Override
   protected void remoteAfterTransform(float deltaTime) {
      if (GameScene.avatar == null || GameScene.avatar.isInside()) {
         return;
      }

      if (this.remoteBiteCooldown > 0.0F) {
         this.remoteBiteCooldown -= deltaTime;
      }

      if (!CollisionDetector.containsPoint(GameScene.avatar.getCameraPos(), this.hitbox, this.position, this.navigator.getDirection().toAngles())) {
         return;
      }

      // The host resolves its own avatar's bite; this one is ours to take.
      if (this.state == State.ATTACKING && this.remoteBiteCooldown <= 0.0F) {
         GameScene.avatar.takeDamage(this.attackDamage, "You were killed by " + this.type.getName());
         this.remoteBiteCooldown = 2.0F;
      }

      GameScene.avatar.applyImpulse(new Point(0.0F, 1.0F, 0.0F).scaled(75.0F));
   }

   @Override
   protected int currentNetStateOrdinal() {
      return this.state.ordinal();
   }

   @Override
   protected void applyNetStateOrdinal(int ordinal) {
      State[] states = State.values();
      if (ordinal >= 0 && ordinal < states.length) {
         this.state = states[ordinal];
      }
   }

   @Override
   protected float currentNetMouth() {
      return this.mouthOpen;
   }

   @Override
   protected void applyNetMouth(float value) {
      this.mouthOpen = value;
   }

   @Override
   protected boolean currentNetDying() {
      return this.navigator.isDying();
   }

   @Override
   protected void applyNetDying() {
      this.navigator.startDying();
   }

   @Override
   public final void renderBody() {
      this.setupBodyRender();
      GL11.glColor4f(1.0F, 1.0F - this.hitFlash * 0.7F, 1.0F - this.hitFlash * 0.7F, this.navigator.getDyingProgress());
      GL11.glPushMatrix();
      GL11.glTranslatef(this.position.x, this.position.y, this.position.z);
      this.navigator.applyGLRotation();
      GL11.glScalef(this.scale, this.scale, this.scale);
      this.bodyMesh.render();
      GL11.glPopMatrix();
   }

   @Override
   public final Damage checkHit(ArrayList<Segment> segments, Damage damage) {
      Damage result = new Damage();
      boolean hit = accumulateSegmentHits(segments, this.hitbox, this.position, this.navigator.getDirection().toAngles(), result);

      if (hit) {
         EnvironmentManager.addBloodParticles(new BloodParticles(result.getSource(), 7));
         this.aggressive = true;
         this.attackInterrupted = true;
         this.mouthOscillation = (float)(Math.PI * 5);
         this.hitFlash = 1.0F;
         result.accumulate(damage);
         // The mirrored copy never mutates health: the host applies the hit.
         if (!this.remoteControlled) {
            this.health = this.health - damage.getAmount();
            if (this.health <= 0.0F) {
               this.navigator.startDying();
            }
         }
      }

      return result;
   }

   @Override
   public final void setTarget(Point target) {
      this.chaseTarget = target;
   }

   @Override
   public final void setupExtraRender() {
   }

   @Override
   public final void renderExtra() {
   }

   @Override
   public final void cleanupExtraRender() {
   }

   @Override
   public void drawExtra() {
   }
}
