package game.shader;

import game.manager.TextureManager;
import game.render.FullscreenQuad;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Fast approximate anti aliasing on the finished frame (runs after bloom and
 * the vignette, so it smooths every edge of the final image).
 */
public final class FxaaEffect {
   private static int textureId = -1;

   private FxaaEffect() {
   }

   public static void render() {
      if (!PostFX.isFxaaEnabled()) {
         return;
      }

      textureId = TextureManager.captureFramebuffer(textureId);
      Shaders.fxaaShader.bind();
      Shaders.setUniform("texelSize", 1.0F / (float)Display.getWidth(), 1.0F / (float)Display.getHeight());
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      FullscreenQuad.drawFlipped();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      Shaders.unbind();
   }
}
