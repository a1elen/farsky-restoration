package game.shader;

import game.Main;
import game.environment.DepthAtmosphere;
import game.manager.TextureManager;
import game.render.FullscreenQuad;
import game.render.Mat4;
import game.render.RenderTexture;
import game.render.SceneTarget;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Scene wide post processing: renders the world into an offscreen target
 * (color + depth, optionally multisampled) and composites it back with ambient
 * occlusion and motion blur. The remaining effects (depth of field, bloom, sun
 * shafts, vignette, FXAA, color grading) run on the back buffer after the
 * composite, following the existing capture based effects.
 */
public final class PostFX {
   public static final int AA_OFF = 0;
   public static final int AA_FXAA = 1;
   public static final int AA_MSAA = 2;
   public static final int AA_BOTH = 3;
   public static int aaMode = AA_FXAA;
   public static int ssaoLevel = 1;
   public static int motionBlurLevel = 0;
   public static int colorGradeLevel = 1;
   public static int bloomLevel = 2;
   public static boolean vignetteEnabled = true;

   private static SceneTarget sceneTarget;
   private static boolean supported = true;
   private static boolean sceneActive = false;
   private static boolean pendingComposite = false;
   private static final RenderTexture aoTarget = new RenderTexture(true);
   private static final RenderTexture aoBlurTarget = new RenderTexture(true);
   private static float[] viewProj = Mat4.identity();
   private static float[] prevViewProj = Mat4.identity();
   private static float[] invViewProj = Mat4.identity();
   private static float[] viewFromWorld = Mat4.identity();
   private static float[] projection = Mat4.identity();
   private static boolean hasPrevMatrices = false;

   private PostFX() {
   }

   public static boolean isMultisampled() {
      return aaMode == AA_MSAA || aaMode == AA_BOTH;
   }

   public static boolean isFxaaEnabled() {
      return aaMode == AA_FXAA || aaMode == AA_BOTH;
   }

   private static boolean needsSceneTarget() {
      return isMultisampled() || ssaoLevel > 0 || motionBlurLevel > 0;
   }

   public static boolean isSceneRendered() {
      return pendingComposite;
   }

   public static int getSceneColorTexture() {
      return sceneTarget == null ? -1 : sceneTarget.getColorTextureId();
   }

   public static int getSceneDepthTexture() {
      return sceneTarget == null ? -1 : sceneTarget.getDepthTextureId();
   }

   public static float[] getInverseProjection() {
      return Mat4.invert(projection);
   }

   public static float[] getProjection() {
      return projection;
   }

   public static float[] getInverseViewProj() {
      return invViewProj;
   }

   public static float[] getViewProj() {
      return viewProj;
   }

   public static float[] getViewFromWorld() {
      return viewFromWorld;
   }

   public static float[] getPrevViewProj() {
      return prevViewProj;
   }

   public static boolean hasPrevMatrices() {
      return hasPrevMatrices;
   }

   public static RenderTexture getAoTarget() {
      return aoTarget;
   }

   public static RenderTexture getAoBlurTarget() {
      return aoBlurTarget;
   }

   public static void beginScene() {
      sceneActive = false;
      if (!supported || !needsSceneTarget()) {
         return;
      }

      if (sceneTarget == null || sceneTarget.isMultisampled() != isMultisampled()) {
         if (sceneTarget != null) {
            sceneTarget.delete();
         }

         sceneTarget = new SceneTarget(isMultisampled());
      }

      if (!sceneTarget.ensureSize(Display.getWidth(), Display.getHeight())) {
         supported = false;
         sceneTarget = null;
         System.err.println("Post processing disabled, scene render target unavailable");
         return;
      }

      sceneTarget.bind();
      sceneActive = true;
   }

   public static void beginWorld() {
      float[] modelView = Mat4.glGet(GL11.GL_MODELVIEW_MATRIX);
      projection = Mat4.glGet(GL11.GL_PROJECTION_MATRIX);
      viewFromWorld = Mat4.invert(modelView);
      float[] currentViewProj = Mat4.multiply(projection, modelView);
      invViewProj = Mat4.invert(currentViewProj);
      prevViewProj = viewProj;
      viewProj = currentViewProj;
      if (!hasPrevMatrices) {
         prevViewProj = currentViewProj;
         hasPrevMatrices = true;
      }
   }

   public static void endScene() {
      if (sceneActive) {
         sceneTarget.resolve();
         sceneTarget.unbind();
         sceneActive = false;
         pendingComposite = true;
      } else {
         pendingComposite = false;
      }
   }

   public static void composite() {
      if (!pendingComposite) {
         return;
      }

      pendingComposite = false;
      boolean useAo = ssaoLevel > 0 && aoTarget.getTextureId() != -1;
      boolean useMotionBlur = motionBlurLevel > 0 && hasPrevMatrices;
      if (!useAo && !useMotionBlur && !isMultisampled()) {
         blit(getSceneColorTexture());
         return;
      }

      Shaders.compositeShader.bind();
      Shaders.setUniform("useAO", useAo);
      Shaders.setUniform("aoStrength", ssaoLevel == 2 ? 0.95F : 0.65F);
      Shaders.setUniform("useMotionBlur", useMotionBlur);
      Shaders.setUniform("motionStrength", motionBlurLevel == 2 ? 1.0F : 0.55F);
      Shaders.setUniform("fogDistance", DepthAtmosphere.getFogDistance());
      Shaders.setUniform("invViewProj", invViewProj);
      Shaders.setUniform("prevViewProj", prevViewProj);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, getSceneColorTexture());
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, useAo ? aoTarget.getTextureId() : getSceneColorTexture());
      GL13.glActiveTexture(GL13.GL_TEXTURE2);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, getSceneDepthTexture());
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      FullscreenQuad.drawFlipped();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      Shaders.unbind();
   }

   private static void blit(int textureId) {
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      TextureManager.drawTexture(textureId, Display.getWidth(), Display.getHeight());
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
   }

   public static void dispose() {
      if (sceneTarget != null) {
         sceneTarget.delete();
         sceneTarget = null;
      }

      aoTarget.delete();
      aoBlurTarget.delete();
      sceneActive = false;
      pendingComposite = false;
   }
}
