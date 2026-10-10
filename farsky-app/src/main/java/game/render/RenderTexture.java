package game.render;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.glu.GLU;

/**
 * Offscreen color render target (framebuffer + color texture) used by the
 * post processing passes: ambient occlusion, depth of field blur, sun shafts.
 */
public final class RenderTexture {
   private int framebufferId = -1;
   private int textureId = -1;
   private int width;
   private int height;
   private final boolean smooth;

   public RenderTexture(boolean smooth) {
      this.smooth = smooth;
   }

   public int getTextureId() {
      return this.textureId;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public void ensureSize(int width, int height) {
      if (width < 1) {
         width = 1;
      }

      if (height < 1) {
         height = 1;
      }

      if (this.framebufferId != -1 && this.width == width && this.height == height) {
         return;
      }

      this.delete();
      this.width = width;
      this.height = height;
      this.framebufferId = GL30.glGenFramebuffers();
      this.textureId = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, this.smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, this.smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
      GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer)null);
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferId);
      GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.textureId, 0);
      GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
      checkStatus();
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
   }

   public static void checkStatus() {
      int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
      if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
         System.err.println("Framebuffer incomplete: 0x" + Integer.toHexString(status));
      }
   }

   public void bind() {
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferId);
      GL11.glViewport(0, 0, this.width, this.height);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glLoadIdentity();
      GLU.gluOrtho2D(0.0F, (float)this.width, (float)this.height, 0.0F);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glLoadIdentity();
   }

   public static void unbind() {
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
      GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glLoadIdentity();
      GLU.gluOrtho2D(0.0F, (float)Display.getWidth(), (float)Display.getHeight(), 0.0F);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glLoadIdentity();
   }

   public void delete() {
      if (this.framebufferId != -1) {
         GL30.glDeleteFramebuffers(this.framebufferId);
         this.framebufferId = -1;
      }

      if (this.textureId != -1) {
         GL11.glDeleteTextures(this.textureId);
         this.textureId = -1;
      }

      this.width = 0;
      this.height = 0;
   }
}
