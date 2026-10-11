package game.input;

import game.Main;
import game.manager.GameState;
import game.manager.RenderManager;
import game.manager.Camera;
import game.manager.GameScene;
import game.submarine.Submarine;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

public final class RawInput {
   public static boolean[] keys = new boolean[68836];
   // Printable characters typed this frame (layout aware, incl. Cyrillic).
   public static final java.util.ArrayList<Character> typedChars = new java.util.ArrayList<>();
   public static boolean rightMouseHeld = false;
   public static boolean leftMouseHeld = false;
   public static boolean rightMouseDown = false;
   public static boolean leftMouseDown = false;
   public static boolean leftMouseReleased = false;
   public static boolean doubleClick = false;
   public static int mouseX;
   public static int mouseY;
   // Mouse movement since the last frame (consumed once per update).
   public static int mouseDX;
   public static int mouseDY;
   public static boolean mouseCaptured = false;
   public static float scrollDelta;
   public static int invertY = 1;
   public static float sensitivity = 1.0F;
   private static float timeSinceLastClick = 0.0F;

   public static void update(float deltaTime) {
      rightMouseDown = false;
      leftMouseDown = false;
      leftMouseReleased = false;
      mouseX = Mouse.getX();
      mouseY = Display.getHeight() - Mouse.getY();
      mouseDX = Mouse.getDX();
      mouseDY = Mouse.getDY();
      scrollDelta = Mouse.getDWheel() * deltaTime;
      Mouse.hasWheel();
      if (!leftMouseHeld && Mouse.isButtonDown(0)) {
         leftMouseDown = true;
         leftMouseHeld = true;
      }

      if (leftMouseHeld && !Mouse.isButtonDown(0)) {
         leftMouseHeld = false;
         leftMouseReleased = true;
      }

      if (!rightMouseHeld && Mouse.isButtonDown(1)) {
         rightMouseDown = true;
         rightMouseHeld = true;
      }

      if (rightMouseHeld && !Mouse.isButtonDown(1)) {
         rightMouseHeld = false;
      }

      doubleClick = false;
      if (leftMouseDown) {
         if (timeSinceLastClick <= 0.3F) {
            doubleClick = true;
         }

         timeSinceLastClick = 0.0F;
      }

      timeSinceLastClick += deltaTime;
      if (Mouse.isInsideWindow() && mouseCaptured && GameScene.avatar != null) {
         if (RenderManager.freeCam || Main.getGameState() != GameState.PLAYING) {
            Camera.input.lookHorizontalDelta = -mouseDX * 8.0F * deltaTime * sensitivity;
            Camera.input.lookVerticalDelta = mouseDY * 8.0F * deltaTime * sensitivity * invertY;
         } else {
            Submarine sub = GameScene.getActiveSubmarine();
            if (GameScene.avatar.isNavigating() && sub != null) {
               sub.input.lookHorizontalDelta = -mouseDX * 8.0F * deltaTime * sensitivity;
               sub.input.lookVerticalDelta = mouseDY * 8.0F * deltaTime * sensitivity * invertY;
            } else {
               GameScene.avatar.input.lookHorizontalDelta = -mouseDX * 8.0F * deltaTime * sensitivity;
               GameScene.avatar.input.lookVerticalDelta = mouseDY * 8.0F * deltaTime * sensitivity * invertY;
            }
         }
      }

      while (Keyboard.next()) {
         if (Keyboard.getEventKeyState()) {
            keys[Keyboard.getEventKey()] = true;
            char typed = Keyboard.getEventCharacter();
            if (typed >= ' ') {
               typedChars.add(typed);
            }
         } else {
            keys[Keyboard.getEventKey()] = false;
         }
      }
   }

   public static int getFirstPressedKey() {
      for (int i = 0; i < keys.length; i++) {
         if (keys[i]) {
            return i;
         }
      }

      return -1;
   }
}
