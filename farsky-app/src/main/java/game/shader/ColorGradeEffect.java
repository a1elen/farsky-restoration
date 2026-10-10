package game.shader;

import game.manager.TextureManager;
import game.render.FullscreenQuad;
import game.util.Point;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Color grading: saturation, contrast and a subtle tint applied to the
 * finished frame.
 */
public final class ColorGradeEffect {
   private static int textureId = -1;

   private ColorGradeEffect() {
   }

   public static void render() {
      if (PostFX.colorGradeLevel == 0) {
         return;
      }

      textureId = TextureManager.captureFramebuffer(textureId);
      boolean vivid = PostFX.colorGradeLevel == 2;
      Shaders.colorGradeShader.bind();
      Shaders.setUniform("saturation", vivid ? 1.30 : 1.10);
      Shaders.setUniform("contrast", vivid ? 1.16 : 1.06);
      Shaders.setUniform("tint", new Point(vivid ? 1.03F : 1.0F, vivid ? 1.0F : 1.005F, vivid ? 1.06F : 1.02F));
      Shaders.setUniform("strength", 1.0);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL11.glEnable(GL11.GL_TEXTURE_2D);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      FullscreenQuad.drawFlipped();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
      Shaders.unbind();
   }
}
