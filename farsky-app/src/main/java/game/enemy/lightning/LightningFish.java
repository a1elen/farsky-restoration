package game.enemy.lightning;

import game.collision.AABB;
import game.collision.CollisionDetector;
import game.enemy.Enemy;
import game.enemy.AI.EnemyNavigator;
import game.enemy.AI.Intelligence;
import game.environment.BloodParticles;
import game.environment.EnvironmentManager;
import game.manager.GameScene;
import game.manager.TextureManager;
import game.player.damage.Damage;
import game.shader.Shaders;
import game.sounds.SoundManager;
import game.manager.GameTime;
import game.util.Point;
import game.util.Segment;

import java.util.ArrayList;
import org.lwjgl.opengl.GL11;

public abstract class LightningFish extends Enemy {
   public static enum State {
      IDLE,
      CHASING,
      SHOCKING,
      DISCHARGING;
   }

   protected AABB hitbox;
   protected State state;
   protected float scale;
   private ArrayList<LightningFlash> lightningFlashes;
   private float flashTimer;
   private float dischargeRadius;
   private EnemyNavigator navigator;
   private float stateCooldown;
   private boolean attackInterrupted = false;
   protected int soundSource;
   private Point chaseTarget;
   private boolean isTargeted = false;
   protected float aggroRange = -1.0F;
   private float nightAggroRange = 300.0F;
   protected float speedMult = 1.0F;
   protected float attackDamage = 10.0F;
   /** Client: previous mirrored state, used to fire one-shot discharge effects. */
   private int remoteVisualState = -1;
   private float remoteFlashTimer = 0.0F;
   private boolean remoteWasInShockRange = false;

   public LightningFish(Point position, float bodySize) {
      this.state = State.IDLE;
      this.position = position.plus(0.0F, 70.0F, 0.0F);
      this.navigator = new EnemyNavigator(this.position, 70.0F, Intelligence.AGGRESSIVE);
      this.stateCooldown = 6.0F;
      this.lightningFlashes = new ArrayList<>();
      this.flashTimer = (float)(0.1F * Math.random());
      this.dischargeRadius = 100.0F;
   }

   protected abstract void onDeath();

   protected abstract void drawMesh();

   @Override
   public final void update(float deltaTime) {
      if (this.hitFlash > 0.0F) {
         this.hitFlash -= deltaTime * 5.0F;
      } else {
         this.hitFlash = 0.0F;
      }

      this.updateCombatFocus();
      Point focus = this.getCombatFocus();
      boolean focusInside = this.focusIsLocalAvatar() && GameScene.avatar.isInside();

      switch (this.state) {
         case IDLE:
            if (focus != null && focus.distanceTo(this.position) < this.aggroRange) {
               this.aggressive = true;
            }

            if (focus != null && GameTime.isNight() && focus.distanceTo(this.position) < this.nightAggroRange) {
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

            if (focusInside) {
               this.stateCooldown = 5.0F;
            }

            if (this.aggressive || this.isTargeted) {
               this.stateCooldown -= deltaTime;
               if (this.stateCooldown <= 0.0F && !focusInside) {
                  this.state = State.SHOCKING;
                  this.stateCooldown = 3.0F;
                  this.attackInterrupted = false;
               }
            } else if (this.chaseTarget != null) {
               this.state = State.CHASING;
            }
            break;
         case CHASING:
            this.isTargeted = true;
            this.navigator.navigate(deltaTime, 70.0F * this.speedMult);
            this.navigator.trySetTarget(this.chaseTarget);
            if (this.position.distanceTo(this.chaseTarget) < 50.0F) {
               this.stateCooldown = 2.0F + (float)Math.random() * 2.0F;
               this.chaseTarget = null;
               this.state = State.IDLE;
            }
            break;
         case SHOCKING:
            this.aggressive = true;
            if (focusInside || focus == null || !this.navigator.trySetTarget(focus) || this.attackInterrupted) {
               this.state = State.IDLE;
               this.attackInterrupted = false;
               this.stateCooldown = 4.0F + (float)Math.random() * 7.0F;
            }

            this.navigator.navigate(deltaTime, 150.0F * this.speedMult);
            if (!this.dead) {
               SoundManager.setLoopingSourceVolume(this.soundSource, 0.8F);
               SoundManager.setLoopingSourcePitch(this.soundSource, 1.8F);
            }

            this.flashTimer += deltaTime;
            if (this.flashTimer >= 0.15F) {
               this.flashTimer -= 0.15F;
               this.lightningFlashes.add(new LightningFlash(this.position, 40.0F));
            }

            this.stateCooldown -= deltaTime;
            if (this.stateCooldown <= 0.0F || (focus != null && focus.distanceTo(this.position) < this.dischargeRadius * 0.8F)) {
               this.state = State.DISCHARGING;
               this.stateCooldown = 4.0F + (float)Math.random() * 7.0F;
            }
            break;
         case DISCHARGING:
            for (int i = 0; i < (int)this.dischargeRadius; i++) {
               this.lightningFlashes.add(new LightningFlash(this.position.plus(new Point(this.dischargeRadius * (Math.random() - 0.5), this.dischargeRadius * (Math.random() - 0.5), this.dischargeRadius * (Math.random() - 0.5))), 40.0F));
            }

            SoundManager.playSound(SoundManager.sfxLightning, this.position, 1.5F + ((float)Math.random() - 0.5F) * 0.2F);
            if (GameScene.avatar.getCameraPos().distanceTo(this.position) < this.dischargeRadius) {
               GameScene.avatar.takeDamage(this.attackDamage, "You were killed by " + this.type.getName());
               GameScene.avatar.applyImpulse(new Point(0.0F, 1.0F, 0.0F).scaled(75.0F));
            }
            this.state = State.IDLE;
            break;
         default:
            break;
      }

      for (int i = this.lightningFlashes.size() - 1; i >= 0; i--) {
         this.lightningFlashes.get(i).tick(deltaTime);
         if (this.lightningFlashes.get(i).isDone()) {
            this.lightningFlashes.remove(i);
         }
      }

      if (CollisionDetector.containsPoint(GameScene.avatar.getCameraPos(), this.hitbox, this.position, this.navigator.getDirection().toAngles())) {
         this.aggressive = true;
         GameScene.avatar.applyImpulse(new Point(0.0F, 1.0F, 0.0F).scaled(75.0F));
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

      this.position = this.navigator.getPosition();
      if (!this.dead) {
         SoundManager.setLoopingSourcePosition(this.soundSource, this.position);
      }
   }

   // ------------------------------------------------------------------
   // Host-authoritative mirror (client side)
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
      if (!this.dead) {
         SoundManager.setLoopingSourcePosition(this.soundSource, this.position);
      }
   }

   @Override
   protected void remoteAfterTransform(float deltaTime) {
      int ord = this.state == null ? State.IDLE.ordinal() : this.state.ordinal();
      // The host only holds DISCHARGING for a single tick, so the discharge is
      // reconstructed from what we can observe: SHOCKING while in bite range,
      // followed by the return to idle.
      boolean inShockRange = this.state == State.SHOCKING
            && GameScene.avatar != null
            && GameScene.avatar.getCameraPos().distanceTo(this.position) < this.dischargeRadius * 0.8F;
      if (this.state == State.SHOCKING && !inShockRange) {
         this.remoteFlashTimer += deltaTime;
         if (this.remoteFlashTimer >= 0.15F) {
            this.remoteFlashTimer -= 0.15F;
            this.lightningFlashes.add(new LightningFlash(this.position, 40.0F));
         }
      }

      boolean justDischarged = this.remoteWasInShockRange && this.state != State.SHOCKING;
      this.remoteWasInShockRange = inShockRange;
      if (justDischarged) {
         for (int i = 0; i < (int)this.dischargeRadius; i++) {
            this.lightningFlashes.add(new LightningFlash(this.position.plus(new Point(
                  this.dischargeRadius * (Math.random() - 0.5F),
                  this.dischargeRadius * (Math.random() - 0.5F),
                  this.dischargeRadius * (Math.random() - 0.5F))), 40.0F));
         }

         SoundManager.playSound(SoundManager.sfxLightning, this.position, 1.5F + ((float)Math.random() - 0.5F) * 0.2F);
         if (GameScene.avatar != null && !GameScene.avatar.isInside()) {
            GameScene.avatar.takeDamage(this.attackDamage, "You were killed by " + this.type.getName());
            GameScene.avatar.applyImpulse(new Point(0.0F, 1.0F, 0.0F).scaled(75.0F));
         }
      }

      this.remoteVisualState = ord;

      for (int i = this.lightningFlashes.size() - 1; i >= 0; i--) {
         this.lightningFlashes.get(i).tick(deltaTime);
         if (this.lightningFlashes.get(i).isDone()) {
            this.lightningFlashes.remove(i);
         }
      }
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
   protected boolean currentNetDying() {
      return this.navigator.isDying();
   }

   @Override
   protected void applyNetDying() {
      this.navigator.startDying();
   }

   @Override
   public final void renderBody() {
      GL11.glColor4f(1.0F, 1.0F - this.hitFlash * 0.7F, 1.0F - this.hitFlash * 0.7F, this.navigator.getDyingProgress());
      GL11.glPushMatrix();
      GL11.glTranslatef(this.position.x, this.position.y, this.position.z);
      this.navigator.applyGLRotation();
      GL11.glScalef(this.scale, this.scale, this.scale);
      this.drawMesh();
      GL11.glPopMatrix();
   }

   @Override
   public final Damage checkHit(ArrayList<Segment> segments, Damage damage) {
      Damage result = new Damage();
      boolean hit = accumulateSegmentHits(segments, this.hitbox, this.position, this.navigator.getDirection().toAngles(), result);

      if (hit) {
         EnvironmentManager.addBloodParticles(new BloodParticles(result.getSource(), 7, BloodParticles.BloodType.BLUE));
         this.aggressive = true;
         this.attackInterrupted = true;
         this.hitFlash = 1.0F;
         result.accumulate(damage);
         // Mirrored copies leave health alone: the host applies the hit.
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
      Shaders.setUniform("emissive", true);
      GL11.glDepthMask(false);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, TextureManager.lightning);
      GL11.glColor4f(0.5F, 0.7F, 1.0F, 1.0F);
   }

   @Override
   public void drawExtra() {
      for (int i = 0; i < this.lightningFlashes.size(); i++) {
         this.lightningFlashes.get(i).render();
      }
   }

   @Override
   public final void cleanupExtraRender() {
      Shaders.setUniform("emissive", false);
      GL11.glDepthMask(true);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @Override
   public final void renderExtra() {
   }
}
