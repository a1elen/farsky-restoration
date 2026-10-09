package game.enemy.jellyFish;

import game.render.SimpleVbo;
import game.render.Vbo;
import game.shader.Shaders;
import game.chunks.ChunkManager;
import game.collision.AABB;
import game.collision.CollisionDetector;
import game.enemy.Enemy;
import game.enemy.EnemyType;
import game.enemy.AI.EnemyNavigator;
import game.enemy.AI.Intelligence;
import game.environment.BloodParticles;
import game.environment.EnvironmentManager;
import game.environment.pickup.ItemPickup;
import game.inventory.ItemType;
import game.manager.GameScene;
import game.manager.TextureManager;
import game.player.damage.Damage;
import game.manager.Frustum;
import game.util.Point;
import game.util.Segment;

import java.util.ArrayList;
import org.lwjgl.opengl.GL11;

public final class JellyFish extends Enemy {
   static enum State {
      IDLE,
      ATTACKING,
      RETREATING;
   }

   public static int texture;
   public static Vbo bodyMesh;
   public static SimpleVbo tentacleMesh;
   private Point position;
   private float phase;
   private AABB hitbox;
   private float currentHealth;
   private float attackCooldown;
   private State state;
   private EnemyNavigator navigator;
   private boolean visible;
   /** Client: local bite cadence while mirroring a host jellyfish that is attacking. */
   private float remoteBiteCooldown = 0.0F;

   public JellyFish(Point spawnPos) {
      this.type = EnemyType.JELLYFISH;
      this.position = spawnPos.plus(0.0F, 150.0F, 0.0F).plus(new Point(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).scaled(70.0F));
      this.navigator = new EnemyNavigator(this.position, 75.0F, Intelligence.PASSIVE);
      this.phase = (float)(Math.random() * Math.PI * 2.0);
      this.hitbox = new AABB(new Point(), 11.0F, 15.0F, 11.0F);
      this.maxHealth = 2.0F;
      this.currentHealth = this.maxHealth;
      this.state = State.IDLE;
      this.attackCooldown = 0.0F;
   }

   @Override
   public final void update(float deltaTime) {
      this.phase += deltaTime * 3.0F;
      if (this.phase >= Math.PI * 2) {
         this.phase = (float)(this.phase - (Math.PI * 2));
      }

      this.updateCombatFocus();
      Point focus = this.getCombatFocus();
      boolean focusInside = this.focusIsLocalAvatar() && GameScene.avatar.isInside();

      switch (this.state) {
         case IDLE:
            this.aggressive = false;
            this.navigator.navigate(deltaTime, 15.0F);
            if (!focusInside) {
               this.attackCooldown -= deltaTime;
               if (this.attackCooldown <= 0.0F && focus != null && focus.distanceTo(this.position) < 150.0F) {
                  this.attackCooldown = (float)Math.random() * 10.0F + 5.0F;
                  this.state = State.ATTACKING;
               }
            } else {
               this.attackCooldown = 5.0F;
            }
            break;
         case ATTACKING:
            this.aggressive = true;
            this.navigator.navigate(deltaTime, 30.0F);
            if (CollisionDetector.containsPoint(GameScene.avatar.getCameraPos(), this.hitbox, new Point(0.0F, (float)Math.cos(this.phase) * 2.0F, 0.0F), new Point())) {
               GameScene.avatar.takeDamage(8.0F, "You were killed by a jellyfish");
               GameScene.avatar.applyImpulse(new Point(0.0F, 1.0F, 0.0F).scaled(75.0F));
               this.state = State.IDLE;
            }

            if (focusInside) {
               this.state = State.IDLE;
            }

            if (focus == null || !this.navigator.trySetTarget(focus) || focus.distanceTo(this.position) > 200.0F) {
               this.state = State.IDLE;
            }
		default:
			break;
      }

      this.position.y = Math.max(ChunkManager.getHeight(this.position.x, this.position.z) + 5.0F, this.position.y);
      this.position = this.navigator.getPosition();
      this.hitbox = new AABB(this.position, 10.0F, 10.0F, 10.0F);
      this.visible = Frustum.isVisible(this.hitbox);
   }

   // ------------------------------------------------------------------
   // Host-authoritative mirror (client side)
   // ------------------------------------------------------------------

   @Override
   protected EnemyNavigator netNavigator() {
      return this.navigator;
   }

   @Override
   protected Point getPositionSnapshot() {
      return this.position;
   }

   @Override
   protected void remoteTransform(float deltaTime) {
      this.phase += deltaTime * 3.0F;
      if (this.phase >= Math.PI * 2) {
         this.phase = (float)(this.phase - (Math.PI * 2));
      }

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
      this.hitbox = new AABB(this.position, 10.0F, 10.0F, 10.0F);
      this.visible = Frustum.isVisible(this.hitbox);
   }

   @Override
   protected void remoteAfterTransform(float deltaTime) {
      if (this.remoteBiteCooldown > 0.0F) {
         this.remoteBiteCooldown -= deltaTime;
      }

      if (this.state != State.ATTACKING || this.remoteBiteCooldown > 0.0F
            || GameScene.avatar == null || GameScene.avatar.isInside()) {
         return;
      }

      if (CollisionDetector.containsPoint(GameScene.avatar.getCameraPos(), this.hitbox, new Point(0.0F, (float)Math.cos(this.phase) * 2.0F, 0.0F), new Point())) {
         GameScene.avatar.takeDamage(8.0F, "You were killed by a jellyfish");
         GameScene.avatar.applyImpulse(new Point(0.0F, 1.0F, 0.0F).scaled(75.0F));
         this.remoteBiteCooldown = 2.0F;
      }
   }

   @Override
   protected float currentNetHealth() {
      return this.currentHealth;
   }

   @Override
   protected void applyNetHealth(float value) {
      this.currentHealth = value;
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
   public final void setupRender() {
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      Shaders.setUniform("axisSign", new Point(1.0F, 1.0F, 1.0F));
      Shaders.setUniform("height", 4.0);
      Shaders.setUniform("alphaLightcolor", new Point(1.0F, 0.5F, 0.5F));
   }

   @Override
   public final void renderBody() {
      if (this.visible) {
         if (this.navigator.isDying()) {
            Shaders.setUniform("alphaLightPercent", 0.2F * this.navigator.getDyingProgress());
         } else {
            Shaders.setUniform("alphaLightPercent", (float)Math.cos(this.phase) * 0.25F + 0.6F);
         }

         GL11.glPushMatrix();
         GL11.glTranslatef(this.position.x, this.position.y + (float)Math.cos(this.phase) * 2.0F, this.position.z);
         this.navigator.applyGLRotation();
         GL11.glRotatef(30.0F, 1.0F, 0.0F, 0.0F);
         GL11.glScalef(5.0F, 5.0F, 5.0F);
         bodyMesh.render();
         GL11.glPopMatrix();
      }
   }

   @Override
   public final void cleanupRender() {
   }

   @Override
   public final void setupExtraRender() {
      Shaders.setUniform("axis", new Point(0.0F, 1.0F, 0.0F));
      Shaders.setUniform("wave", new Point(1.0F, 0.0F, 1.0F));
      Shaders.setUniform("factor", 8.0);
      Shaders.setUniform("topLight", false);
      Shaders.setUniform("axisSign", new Point(-1.0F, -1.0F, -1.0F));
      Shaders.setUniform("height", 5.0);
      Shaders.setUniform("alphaLightcolor", new Point(1.0F, 1.0F, 1.0F));
      GL11.glLineWidth(1.0F);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, TextureManager.alphaTexture);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
   }

   @Override
   public final void renderExtra() {
      if (this.visible) {
         if (this.navigator.isDying()) {
            Shaders.setUniform("alphaLightPercent", 0.2F * this.navigator.getDyingProgress());
         } else {
            Shaders.setUniform("alphaLightPercent", (float)Math.cos(this.phase) * 0.25F + 0.6F);
         }

         GL11.glPushMatrix();
         GL11.glTranslatef(this.position.x, this.position.y + (float)Math.cos(this.phase) * 2.0F, this.position.z);
         this.navigator.applyGLRotation();
         GL11.glRotatef(30.0F, 1.0F, 0.0F, 0.0F);
         GL11.glScalef(5.0F, 5.0F, 5.0F);
         tentacleMesh.render();
         GL11.glPopMatrix();
      }
   }

   @Override
   public final void cleanupExtraRender() {
      Shaders.setUniform("axis", new Point(0.0F, 0.0F, 0.0F));
      Shaders.setUniform("wave", new Point(0.0F, 0.0F, 0.0F));
   }

   @Override
   public final Point getPosition() {
      return this.position;
   }

   @Override
   public final void onRemove() {
   }

   @Override
   public final Damage checkHit(ArrayList<Segment> segments, Damage damage) {
      boolean hit = false;
      Damage result = new Damage();

      for (int i = 0; i < segments.size(); i++) {
         if (CollisionDetector.segmentIntersects(segments.get(i), this.hitbox, new Point(0.0F, (float)Math.cos(this.phase) * 2.0F, 0.0F), new Point())) {
            hit = true;
            result.setSource(segments.get(i).start);
         }
      }

      if (hit) {
         EnvironmentManager.addBloodParticles(new BloodParticles(result.getSource(), 7, BloodParticles.BloodType.BLUE));
         result.accumulate(damage);
         // Mirrored copies leave health alone: the host applies the hit.
         if (!this.remoteControlled) {
            this.currentHealth = this.currentHealth - damage.getAmount();
            if (this.currentHealth <= 0.0F) {
               EnvironmentManager.addItemPickup(new ItemPickup(this.position, ItemType.JELLY));
               this.currentHealth = 0.0F;
               // Matches the original: die() marks the corpse dead so further
               // hits are ignored instead of dropping another jelly each time.
               this.die(null);
            }
         }
      }

      return result;
   }

   @Override
   public final void setTarget(Point target) {
   }

   @Override
   public final boolean isAtChunk(int chunkX, int chunkZ) {
      return false;
   }
}
