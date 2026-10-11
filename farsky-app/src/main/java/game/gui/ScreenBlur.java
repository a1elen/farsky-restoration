package game.gui;

import game.Main;
import game.gui.util.MenuBackground;
import game.manager.TextureManager;
import game.shader.BlurEffect;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class ScreenBlur {
   private static float blurAmount = 0.0F;

   public static void update(float delta) {
      if (blurAmount < 1.0F) {
         blurAmount += delta * 5.0F;
      } else {
         blurAmount = 1.0F;
      }

      if (Main.hasStateChanged()) {
         blurAmount = 0.0F;
         MenuBackground.captureTexture = TextureManager.captureFramebuffer(MenuBackground.captureTexture);
      }
   }

   /**
    * Captures the live world as the blurred backdrop. Called right after the
    * world pass (before any menu chrome is drawn) because the menus no longer
    * pause the world - the blur must follow the action, not a snapshot.
    */
   public static void captureBackdrop() {
      MenuBackground.captureTexture = TextureManager.captureFramebuffer(MenuBackground.captureTexture);
   }

   public static void render() {
      GL11.glDisable(GL11.GL_DEPTH_TEST);
      BlurEffect.apply(MenuBackground.captureTexture, 2.0F / Display.getWidth() * blurAmount, blurAmount * 0.25F, 1.0F - blurAmount * 1.75F);
      GL11.glEnable(GL11.GL_DEPTH_TEST);
   }
}
