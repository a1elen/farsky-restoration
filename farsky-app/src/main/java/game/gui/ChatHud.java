package game.gui;

import game.input.RawInput;
import game.net.NetSession;
import game.util.FontFamily;
import game.util.FontRenderer;
import java.util.ArrayList;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * In-game text chat: Enter opens the input line, Enter sends, Esc cancels.
 * Characters come from Keyboard.getEventCharacter(), so any OS keyboard
 * layout (including Cyrillic) types as usual. Messages fade after a while.
 */
public final class ChatHud {
   private static final int MAX_MESSAGE_LENGTH = 160;
   private static final int MAX_VISIBLE_MESSAGES = 8;
   private static final float MESSAGE_LIFETIME = 12.0F;
   private static final ArrayList<String> messages = new ArrayList<>();
   private static final ArrayList<Float> messageTimers = new ArrayList<>();
   private static boolean prevEnter = false;
   private static boolean prevEsc = false;
   private static boolean prevBack = false;
   private static boolean inputOpen = false;
   private static String inputBuffer = "";

   private ChatHud() {
   }

   public static boolean isInputOpen() {
      return inputOpen;
   }

   public static void showMessage(String text) {
      if (text == null || text.isEmpty()) {
         return;
      }

      messages.add(text);
      messageTimers.add(MESSAGE_LIFETIME);
      while (messages.size() > MAX_VISIBLE_MESSAGES) {
         messages.remove(0);
         messageTimers.remove(0);
      }
   }

   public static void update(float delta) {
      for (int i = messageTimers.size() - 1; i >= 0; i--) {
         float timer = messageTimers.get(i) - delta;
         if (timer <= 0.0F) {
            messages.remove(i);
            messageTimers.remove(i);
         } else {
            messageTimers.set(i, timer);
         }
      }
   }

   /**
    * Handles the chat keyboard while the game is in the PLAYING state.
    *
    * @return true while the input line captures keys (game input must be skipped)
    */
   public static boolean captureInput() {
      boolean enter = RawInput.keys[Keyboard.KEY_RETURN];
      boolean esc = RawInput.keys[Keyboard.KEY_ESCAPE];
      boolean back = RawInput.keys[Keyboard.KEY_BACK];
      boolean enterPressed = enter && !prevEnter;
      boolean escPressed = esc && !prevEsc;
      boolean backPressed = back && !prevBack;
      prevEnter = enter;
      prevEsc = esc;
      prevBack = back;

      if (!inputOpen) {
         // discard characters typed while no chat line is shown
         RawInput.typedChars.clear();
         if (enterPressed && NetSession.isActive()) {
            inputOpen = true;
            inputBuffer = "";
         }

         return inputOpen;
      }

      for (int i = 0; i < RawInput.typedChars.size(); i++) {
         if (inputBuffer.length() < MAX_MESSAGE_LENGTH) {
            inputBuffer = inputBuffer + RawInput.typedChars.get(i);
         }
      }

      RawInput.typedChars.clear();
      if (escPressed) {
         inputOpen = false;
         inputBuffer = "";
         return false;
      }

      if (enterPressed) {
         inputOpen = false;
         String text = inputBuffer.trim();
         inputBuffer = "";
         if (!text.isEmpty()) {
            NetSession.sendChat(text);
            showMessage(text);
         }

         return false;
      }

      if (backPressed && inputBuffer.length() > 0) {
         inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
      }

      return true;
   }

   public static void render() {
      boolean anyVisible = !messages.isEmpty();
      if (!anyVisible && !inputOpen) {
         return;
      }

      FontRenderer.saveFontFamily();
      FontRenderer.setFontFamily(FontFamily.CHAPARRAL);
      float y = Display.getHeight() - 300.0F;
      for (int i = 0; i < messages.size(); i++) {
         float alpha = Math.min(1.0F, messageTimers.get(i) / 2.0F);
         GL11.glColor4f(0.95F, 0.97F, 1.0F, alpha);
         FontRenderer.draw(24, (int)y, messages.get(i), 0.5F);
         y += 22.0F;
      }

      if (inputOpen) {
         String line = "> " + inputBuffer + "_";
         int panelY = Display.getHeight() - 56;
         int panelWidth = FontRenderer.getTextWidth(line, 0.5F) + 24;
         GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.55F);
         GL11.glBegin(GL11.GL_QUADS);
         GL11.glVertex2f(20.0F, (float)panelY - 6.0F);
         GL11.glVertex2f(20.0F + panelWidth, (float)panelY - 6.0F);
         GL11.glVertex2f(20.0F + panelWidth, (float)panelY + FontRenderer.getCharHeight(0.5F) + 8.0F);
         GL11.glVertex2f(20.0F, (float)panelY + FontRenderer.getCharHeight(0.5F) + 8.0F);
         GL11.glEnd();
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         FontRenderer.draw(32, panelY, line, 0.5F);
      }

      FontRenderer.restoreFontFamily();
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
   }
}
