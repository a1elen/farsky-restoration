package game.render;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.glu.GLU;

/**
 * Scene render target used by the post processing stack: one color texture and
 * one depth texture, optionally backed by multisampled renderbuffers which are
 * resolved (blitted) into the sampleable textures at the end of the scene pass.
 */
public final class SceneTarget {
   private int framebufferId = -1;
   private int colorTextureId = -1;
   private int depthTextureId = -1;
   private int multisampleFramebufferId = -1;
   private int colorRenderbufferId = -1;
   private int depthRenderbufferId = -1;
   private int width;
   private int height;
   private int samples;
   private final boolean multisampled;

   public SceneTarget(boolean multisampled) {
      this.multisampled = multisampled;
   }

   public int getColorTextureId() {
      return this.colorTextureId;
   }

   public int getDepthTextureId() {
      return this.depthTextureId;
   }

   public boolean isMultisampled() {
      return this.multisampled;
   }

   public boolean ensureSize(int width, int height) {
      if (width < 1 || height < 1) {
         return false;
      }

      if (this.framebufferId != -1 && this.width == width && this.height == height) {
         return true;
      }

      this.delete();
      this.width = width;
      this.height = height;

      try {
         this.colorTextureId = GL11.glGenTextures();
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.colorTextureId);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer)null);

         this.depthTextureId = GL11.glGenTextures();
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.depthTextureId);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (java.nio.ByteBuffer)null);

         this.framebufferId = GL30.glGenFramebuffers();
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferId);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.colorTextureId, 0);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, this.depthTextureId, 0);
         RenderTexture.checkStatus();

         if (this.multisampled) {
            int maxSamples = GL11.glGetInteger(GL30.GL_MAX_SAMPLES);
            this.samples = Math.max(1, Math.min(4, maxSamples));
            if (this.samples > 1) {
               this.multisampleFramebufferId = GL30.glGenFramebuffers();
               this.colorRenderbufferId = GL30.glGenRenderbuffers();
               this.depthRenderbufferId = GL30.glGenRenderbuffers();
               GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.multisampleFramebufferId);
               GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.colorRenderbufferId);
               GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, this.samples, GL11.GL_RGBA8, width, height);
               GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, this.colorRenderbufferId);
               GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.depthRenderbufferId);
               GL30.glRenderbufferStorageMultisample(GL30.GL_RENDERBUFFER, this.samples, GL14.GL_DEPTH_COMPONENT24, width, height);
               GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, this.depthRenderbufferId);
               RenderTexture.checkStatus();
               if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                  this.deleteMsaa();
               }
            }
         }

         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebufferId);
         boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
         if (!complete) {
            this.delete();
         }

         return complete;
      } catch (RuntimeException e) {
         System.err.println("Scene render target unavailable: " + e);
         this.delete();
         return false;
      }
   }

   public void bind() {
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.multisampleFramebufferId != -1 ? this.multisampleFramebufferId : this.framebufferId);
      GL11.glViewport(0, 0, this.width, this.height);
      GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
   }

   public void resolve() {
      if (this.multisampleFramebufferId == -1) {
         return;
      }

      GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.multisampleFramebufferId);
      GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.framebufferId);
      GL30.glBlitFramebuffer(0, 0, this.width, this.height, 0, 0, this.width, this.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
      GL30.glBlitFramebuffer(0, 0, this.width, this.height, 0, 0, this.width, this.height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
   }

   public void unbind() {
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
      GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glLoadIdentity();
      GLU.gluOrtho2D(0.0F, (float)Display.getWidth(), (float)Display.getHeight(), 0.0F);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glLoadIdentity();
      GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
   }

   private void deleteMsaa() {
      if (this.multisampleFramebufferId != -1) {
         GL30.glDeleteFramebuffers(this.multisampleFramebufferId);
         this.multisampleFramebufferId = -1;
      }

      if (this.colorRenderbufferId != -1) {
         GL30.glDeleteRenderbuffers(this.colorRenderbufferId);
         this.colorRenderbufferId = -1;
      }

      if (this.depthRenderbufferId != -1) {
         GL30.glDeleteRenderbuffers(this.depthRenderbufferId);
         this.depthRenderbufferId = -1;
      }
   }

   public void delete() {
      this.deleteMsaa();
      if (this.framebufferId != -1) {
         GL30.glDeleteFramebuffers(this.framebufferId);
         this.framebufferId = -1;
      }

      if (this.colorTextureId != -1) {
         GL11.glDeleteTextures(this.colorTextureId);
         this.colorTextureId = -1;
      }

      if (this.depthTextureId != -1) {
         GL11.glDeleteTextures(this.depthTextureId);
         this.depthTextureId = -1;
      }

      this.width = 0;
      this.height = 0;
   }
}
