package game.net;

import game.environment.DepthAtmosphere;
import game.environment.SkyDome;
import game.manager.Camera;
import game.manager.GameTime;
import game.manager.TextureManager;
import game.shader.Shaders;
import game.util.Point;
import org.lwjgl.opengl.GL11;

/**
 * Position of the other peer in the session, interpolated for smooth rendering.
 *
 * <p>The remote player is drawn as a camera facing billboard using the player
 * portrait texture, because FarSky only has a first person model for the local
 * avatar (no third person body to reuse).</p>
 */
public final class RemotePlayer {
   private static final float SMOOTHING = 15.0F;
   private static final float WIDTH = 9.0F;
   private static final float HEIGHT = 24.0F;

   private final Point targetPos = new Point();
   private final Point renderPos = new Point();
   private float targetYaw;
   private float renderYaw;
   private boolean initialized = false;
   private boolean navigating = false;
   // Multiplayer identity: host = 0, clients = 1..7 (assigned at handshake).
   private int id = 0;
   private String nickname = "Player";
   // Submarine slot this player is currently driving (-1 = on foot).
   private int subIdx = -1;
   // Screen position of this player's nametag (set during the 3D pass).
   private boolean nametagVisible = false;
   private float nametagX = 0.0F;
   private float nametagY = 0.0F;

   public final void setId(int value) {
      this.id = value;
   }

   public final int getId() {
      return this.id;
   }

   public final void setNickname(String value) {
      this.nickname = value == null || value.isEmpty() ? "Player" : value;
   }

   public final String getNickname() {
      return this.nickname;
   }

   public final void setSubIdx(int value) {
      this.subIdx = value;
   }

   public final int getSubIdx() {
      return this.subIdx;
   }

   public final void setNametag(boolean visible, float x, float y) {
      this.nametagVisible = visible;
      this.nametagX = x;
      this.nametagY = y;
   }

   public final boolean isNametagVisible() {
      return this.nametagVisible;
   }

   public final float getNametagX() {
      return this.nametagX;
   }

   public final float getNametagY() {
      return this.nametagY;
   }

   /** True while the peer is inside a submarine: their billboard is replaced by the boat. */
   public final void setNavigating(boolean value) {
      this.navigating = value;
   }

   public final boolean isNavigating() {
      return this.navigating;
   }

   public final void setTarget(float x, float y, float z, float yaw) {
      this.targetPos.x = x;
      this.targetPos.y = y;
      this.targetPos.z = z;
      this.targetYaw = yaw;
      if (!this.initialized) {
         this.renderPos.set(this.targetPos);
         this.renderYaw = yaw;
         this.initialized = true;
      }
   }

   public final Point getRenderPos() {
      return this.renderPos;
   }

   public final float getRenderYaw() {
      return this.renderYaw;
   }

   public final void update(float delta) {
      if (!this.initialized) {
         return;
      }

      float k = (float)(1.0 - Math.exp((double)(-SMOOTHING * delta)));
      this.renderPos.x += (this.targetPos.x - this.renderPos.x) * k;
      this.renderPos.y += (this.targetPos.y - this.renderPos.y) * k;
      this.renderPos.z += (this.targetPos.z - this.renderPos.z) * k;
      float yawDiff = ((this.targetYaw - this.renderYaw + 540.0F) % 360.0F) - 180.0F;
      this.renderYaw += yawDiff * k;
   }

   /**
    * Must be called while the enemy shader is bound, after the seafloor bases'
    * opaque pass and before their glass (renderAlpha) pass, with the world
    * camera applied.
    */
   public final void render() {
      if (!this.initialized || this.navigating) {
         return;
      }

      Point campos = Camera.getPosition();
      float fx = this.renderPos.x - campos.x;
      float fz = this.renderPos.z - campos.z;
      float len = (float)Math.sqrt((double)(fx * fx + fz * fz));
      if (len < 0.001F) {
         fx = 1.0F;
         fz = 0.0F;
         len = 1.0F;
      }

      // Right vector of the billboard as seen by the camera (cross(forward, up)).
      float rightX = -fz / len * WIDTH;
      float rightZ = fx / len * WIDTH;
      float centerY = this.renderPos.y + HEIGHT * 0.55F;
      float leftX = this.renderPos.x - rightX;
      float leftZ = this.renderPos.z - rightZ;
      float rightPosX = this.renderPos.x + rightX;
      float rightPosZ = this.renderPos.z + rightZ;
      float bottom = centerY - HEIGHT * 0.5F;
      float top = centerY + HEIGHT * 0.5F;

      setupUniforms();
      GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

      try {
         GL11.glEnable(GL11.GL_BLEND);
         GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
         GL11.glDisable(GL11.GL_CULL_FACE);
         GL11.glDepthMask(false);
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, TextureManager.avatarPortrait);
         GL11.glBegin(GL11.GL_QUADS);

         GL11.glNormal3f(-fx / len, 0.0F, -fz / len);
         // V=0 is the top of the portrait texture (same convention as PlayerHud).
         GL11.glTexCoord2f(0.0F, 1.0F);
         GL11.glVertex3f(leftX, bottom, leftZ);
         GL11.glTexCoord2f(1.0F, 1.0F);
         GL11.glVertex3f(rightPosX, bottom, rightPosZ);
         GL11.glTexCoord2f(1.0F, 0.0F);
         GL11.glVertex3f(rightPosX, top, rightPosZ);
         GL11.glTexCoord2f(0.0F, 0.0F);
         GL11.glVertex3f(leftX, top, leftZ);
         GL11.glEnd();
      } finally {
         GL11.glPopAttrib();
      }
   }

   private static void setupUniforms() {
      Shaders.setUniform("time", (double)GameTime.elapsedMillis);
      // The enemy vertex shader displaces vertices with a wave; wave = 0 disables it.
      Shaders.setUniform("axis", new Point(0.0F, 1.0F, 0.0F));
      Shaders.setUniform("wave", new Point(0.0F, 0.0F, 0.0F));
      Shaders.setUniform("axisSign", new Point(1.0F, 1.0F, 1.0F));
      Shaders.setUniform("height", 1.0);
      Shaders.setUniform("factor", 0.0);
      Shaders.setUniform("offset", 0.0);
      Shaders.setUniform("topLight", true);
      Shaders.setUniform("topLightPos", new Point(0.0F, 1000.0F, 0.0F));
      Shaders.setUniform("emissive", false);
      Shaders.setUniform("alphaLightPercent", 0.0);
      Shaders.setUniform("alphaLightcolor", new Point(1.0F, 1.0F, 1.0F));
      Shaders.setUniform("lightLimit", 100.0);
      Shaders.setUniform("visibleLimit", (double)DepthAtmosphere.getFogDistance());
      Shaders.setUniform("glowColor", SkyDome.skyColor);
   }
}
