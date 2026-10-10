package game.render;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Minimal column-major 4x4 matrix helper (OpenGL layout, column vectors).
 * Used by the post processing stack for depth reconstruction, shadow lookups
 * and motion blur reprojection. Kept free of LWJGL's Matrix4f because the
 * coordinate conventions of the two differ.
 */
public final class Mat4 {
   private Mat4() {
   }

   public static float[] identity() {
      float[] m = new float[16];
      m[0] = 1.0F;
      m[5] = 1.0F;
      m[10] = 1.0F;
      m[15] = 1.0F;
      return m;
   }

   public static float[] multiply(float[] a, float[] b) {
      float[] r = new float[16];

      for (int col = 0; col < 4; col++) {
         for (int row = 0; row < 4; row++) {
            float sum = 0.0F;

            for (int k = 0; k < 4; k++) {
               sum += a[k * 4 + row] * b[col * 4 + k];
            }

            r[col * 4 + row] = sum;
         }
      }

      return r;
   }

   public static float[] perspective(float fovDegrees, float aspect, float near, float far) {
      float[] m = new float[16];
      float f = (float)(1.0 / Math.tan(Math.toRadians(fovDegrees) / 2.0));
      m[0] = f / aspect;
      m[5] = f;
      m[10] = (far + near) / (near - far);
      m[11] = -1.0F;
      m[14] = 2.0F * far * near / (near - far);
      return m;
   }

   public static float[] invert(float[] m) {
      float[] inv = new float[16];
      inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
      inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
      inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
      inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
      inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
      inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
      inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
      inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
      inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
      inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
      inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
      inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
      inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
      inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
      inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
      inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

      float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
      if (det == 0.0F) {
         return identity();
      }

      det = 1.0F / det;

      for (int i = 0; i < 16; i++) {
         inv[i] = inv[i] * det;
      }

      return inv;
   }

   public static float[] project(float[] matrix, float x, float y, float z) {
      float[] clip = new float[4];
      clip[0] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
      clip[1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
      clip[2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
      clip[3] = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15];
      return clip;
   }

   public static float[] glGet(int matrixName) {
      FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
      GL11.glGetFloat(matrixName, buffer);
      float[] result = new float[16];
      buffer.rewind();
      buffer.get(result);
      return result;
   }

   public static FloatBuffer toBuffer(float[] matrix) {
      FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
      buffer.put(matrix);
      buffer.rewind();
      return buffer;
   }
}
