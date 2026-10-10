package game.shader;

import game.environment.DepthAtmosphere;
import game.manager.TextureManager;
import game.render.FullscreenQuad;
import game.render.RenderTexture;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Screen space ambient occlusion. Reads the depth texture of the scene render
 * target, reconstructs view space positions and darkens contact areas (rocks,
 * sea grass, bases and other geometry meeting the sea floor).
 *
 * The raw buffer only samples a handful of times per pixel, so it is grainy. It
 * is cleaned up with two separable depth aware blur passes before the composite
 * pass reads it; the depth weighting keeps the blur from smearing the occlusion
 * across silhouettes, which is what used to make edges look jagged.
 */
public final class SsaoEffect {
   private SsaoEffect() {
   }

   public static void render() {
      int depthTexture = PostFX.getSceneDepthTexture();
      if (PostFX.ssaoLevel == 0 || depthTexture == -1) {
         return;
      }

      RenderTexture target = PostFX.getAoTarget();
      int width = Display.getWidth() / 2;
      int height = Display.getHeight() / 2;
      target.ensureSize(width, height);
      target.bind();
      Shaders.ssaoShader.bind();
      Shaders.setUniform("invProjection", PostFX.getInverseProjection());
      Shaders.setUniform("projection", PostFX.getProjection());
      Shaders.setUniform("radius", PostFX.ssaoLevel == 2 ? 9.0 : 6.0);
      Shaders.setUniform("bias", 0.025);
      Shaders.setUniform("fogDistance", DepthAtmosphere.getFogDistance());
      Shaders.setUniform("depthTexel", 1.0F / (float)Display.getWidth(), 1.0F / (float)Display.getHeight());
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      TextureManager.drawTexture(depthTexture, width, height);
      Shaders.unbind();
      RenderTexture.unbind();
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      blur(target, PostFX.getAoBlurTarget(), width, height, 1.0F / (float)width, 0.0F);
      blur(PostFX.getAoBlurTarget(), target, width, height, 0.0F, 1.0F / (float)height);
   }

   /**
    * One separable blur pass. Reads the occlusion from and writes the result to
    * the given target; both passes ping pong between the two half resolution
    * buffers so the final blurred result ends up back in the main AO buffer.
    */
   private static void blur(RenderTexture from, RenderTexture to, int width, int height, float dirX, float dirY) {
      to.ensureSize(width, height);
      to.bind();
      Shaders.aoBlurShader.bind();
      Shaders.setUniform("blurDir", dirX, dirY);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, from.getTextureId());
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, PostFX.getSceneDepthTexture());
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      FullscreenQuad.drawFlipped(width, height);
      Shaders.unbind();
      RenderTexture.unbind();
      GL13.glActiveTexture(GL13.GL_TEXTURE1);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
   }
}
