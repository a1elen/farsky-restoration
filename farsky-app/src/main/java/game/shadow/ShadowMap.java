package game.shadow;

import game.Main;
import game.chunks.ChunkManager;
import game.manager.Camera;
import game.manager.GameTime;
import game.manager.RenderManager;
import game.render.Mat4;
import game.shader.Shaders;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Top down shadow map. The world light always comes from straight above
 * (see topLightPos in the world shaders), so an orthographic depth pass around
 * the camera is enough: terrain, chunk elements (rocks, corals, grass, kelp)
 * and everything else drawn by the opaque element pass casts shadows that are
 * sampled by the world and world floor shaders.
 */
public final class ShadowMap {
   public static boolean shadowsEnabled = true;
   /** 1 = soft (PCF filtered), 2 = hard edges. */
   public static int shadowQuality = 1;
   public static int shadowResolution = 2048;
   /**
    * Shadow distance scale, mirroring the render distance options
    * (Tiny/Short/Normal/High/Very High/Ultra = 0.5/0.75/1.0/1.25/1.5/2.0).
    * "Normal" shadow distance matches "Normal" render distance.
    */
   public static float shadowDistanceFactor = 1.0F;
   private static final float baseRadius = 300.0F;
   private static float lightRange = 1200.0F;
   /** World units covered by one shadow map texel, used for the normal offset. */
   private static float shadowWorldTexel = 0.1F;
   private static int framebufferId = -1;
   private static int depthTextureId = -1;
   private static int textureSize = 0;
   private static float[] matrix = Mat4.identity();
   private static boolean valid = false;

   private ShadowMap() {
   }

   public static boolean isEnabled() {
      return shadowsEnabled;
   }

   public static boolean isValid() {
      return valid && isEnabled();
   }

   public static float[] getMatrix() {
      return matrix;
   }

   public static int getDepthTextureId() {
      return depthTextureId;
   }

   public static void bindTexture() {
      if (depthTextureId == -1) {
         return;
      }

      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTextureId);
      GL13.glActiveTexture(GL13.GL_TEXTURE3);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTextureId);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
   }

   /**
    * Must be called right after a world or world floor shader bind: uploads the
    * shadow uniforms (the matrices were captured by PostFX.beginWorld) and
    * binds the depth texture on the units the shader expects.
    */
   public static void applyUniforms() {
      boolean active = isValid();
      Shaders.setUniform("shadowEnabled", active);
      if (!active) {
         return;
      }

      Shaders.setUniform("shadowMatrix", matrix);
      Shaders.setUniform("viewFromWorld", game.shader.PostFX.getViewFromWorld());
      Shaders.setUniform("shadowTexel", 1.0F / (float)textureSize);
      Shaders.setUniform("shadowWorldTexel", shadowWorldTexel);
      Shaders.setUniform("shadowDarkness", getShadowDarkness());
      Shaders.setUniform("shadowSoft", shadowQuality == 1);
      bindTexture();
   }

   /**
    * How strongly shadows darken the scene. Fades out as night falls so the
    * scene does not keep harsh noon shadows under ambient night light.
    */
   private static float getShadowDarkness() {
      float t = (game.manager.GameTime.getLightLevel() - 0.3F) / 0.7F;
      if (t < 0.0F) {
         t = 0.0F;
      }

      if (t > 1.0F) {
         t = 1.0F;
      }

      return 0.45F * t;
   }

   public static void render() {
      valid = false;
      if (!isEnabled() || !ensureTarget()) {
         return;
      }

      float radius = baseRadius * shadowDistanceFactor;
      float[] camera = getCameraPosition();
      shadowWorldTexel = 2.0F * radius / (float)textureSize;

      // The sun elevation follows the day cycle: straight down around noon and
      // tilted toward the horizon in the morning / evening, so shadows lengthen
      // and shift through the day instead of staying fixed.
      float elevation = GameTime.getSunElevation();
      if (elevation > 85.0F) {
         elevation = 85.0F;
      }

      if (elevation < 20.0F) {
         elevation = 20.0F;
      }

      float tilt = 90.0F - elevation;
      if (tilt > 50.0F) {
         tilt = 50.0F;
      }

      double tiltRad = Math.toRadians((double)tilt);
      float dirY = -(float)Math.cos(tiltRad);
      float dirZ = -(float)Math.sin(tiltRad);
      // Light origin: back off half the shadow range along the light direction
      // so the camera stays in the middle of the orthographic volume.
      float originX = camera[0];
      float originY = camera[1] - dirY * lightRange * 0.5F;
      float originZ = camera[2] - dirZ * lightRange * 0.5F;
      int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

      GL11.glPushAttrib(GL11.GL_VIEWPORT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TRANSFORM_BIT | GL11.GL_POLYGON_BIT);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glPushMatrix();
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glPushMatrix();

      try {
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
         GL11.glViewport(0, 0, textureSize, textureSize);
         GL11.glDrawBuffer(GL11.GL_NONE);
         GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
         GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
         GL11.glColorMask(true, true, true, true);
         GL11.glEnable(GL11.GL_DEPTH_TEST);
         GL11.glDepthFunc(GL11.GL_LEQUAL);
         GL11.glDepthMask(true);
         GL11.glDisable(GL11.GL_BLEND);
         GL11.glEnable(GL11.GL_CULL_FACE);
         GL11.glCullFace(GL11.GL_BACK);

         GL11.glMatrixMode(GL11.GL_PROJECTION);
         GL11.glLoadIdentity();
         GL11.glOrtho(-radius, radius, -radius, radius, 1.0, lightRange);
         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glLoadIdentity();
         GL11.glRotatef(90.0F - tilt, 1.0F, 0.0F, 0.0F);
         GL11.glTranslatef(-originX, -originY, -originZ);
         matrix = Mat4.multiply(Mat4.glGet(GL11.GL_PROJECTION_MATRIX), Mat4.glGet(GL11.GL_MODELVIEW_MATRIX));

         Shaders.shadowTerrainShader.bind();
         Shaders.setUniform("time", GameTime.elapsedMillis);
         RenderManager.enableCubemap();
         ChunkManager.renderTerrain();
         RenderManager.disableCubemap();
         Shaders.shadowShader.bind();
         Shaders.setUniform("time", GameTime.elapsedMillis);
         ChunkManager.renderElements();
         Shaders.unbind();
         valid = true;
      } finally {
         GL11.glMatrixMode(GL11.GL_PROJECTION);
         GL11.glPopMatrix();
         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glPopMatrix();
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
         GL11.glPopAttrib();
         GL11.glColorMask(true, true, true, true);
         GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
      }
   }

   private static float[] getCameraPosition() {
      game.util.Point pos = Camera.getPosition();
      return new float[]{pos.x, pos.y, pos.z};
   }

   private static boolean ensureTarget() {
      int size = shadowResolution;
      if (depthTextureId != -1 && textureSize == size) {
         return true;
      }

      delete();
      textureSize = size;
      depthTextureId = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTextureId);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER);
      float[] border = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
      FloatBuffer borderBuffer = BufferUtils.createFloatBuffer(4);
      borderBuffer.put(border).flip();
      GL11.glTexParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_BORDER_COLOR, borderBuffer);
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL14.GL_DEPTH_COMPONENT24, size, size, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (java.nio.ByteBuffer)null);
      framebufferId = GL30.glGenFramebuffers();
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
      GL11.glDrawBuffer(GL11.GL_NONE);
      GL11.glReadBuffer(GL11.GL_NONE);
      GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTextureId, 0);
      boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      if (!complete) {
         System.err.println("Shadow map framebuffer incomplete");
         delete();
      }

      return complete;
   }

   public static void dispose() {
      delete();
      valid = false;
   }

   private static void delete() {
      if (framebufferId != -1) {
         GL30.glDeleteFramebuffers(framebufferId);
         framebufferId = -1;
      }

      if (depthTextureId != -1) {
         GL11.glDeleteTextures(depthTextureId);
         depthTextureId = -1;
      }

      textureSize = 0;
   }
}
