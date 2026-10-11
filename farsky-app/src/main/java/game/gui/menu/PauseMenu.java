package game.gui.menu;

import game.Main;
import game.gui.util.Button;
import game.gui.util.ButtonType;
import game.manager.GameScene;
import game.manager.GameState;
import game.net.NetSession;
import game.util.FontFamily;
import game.util.FontRenderer;
import java.util.ArrayList;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class PauseMenu extends MenuScreen {
   public PauseMenu() {
      this.buttons.add(new Button(ButtonType.MENU_BUTTON, 0, "Resume"));
      this.buttons.add(new Button(ButtonType.MENU_BUTTON, 1, "Options"));
      this.buttons.add(new Button(ButtonType.MENU_BUTTON, 2, "Save & Quit"));
   }

   @Override
   public final void draw() {
      for (int i = 0; i < this.buttons.size(); i++) {
         this.buttons.get(i).render();
      }

      renderPlayerList();
   }

   /**
    * Multiplayer panel: every connected player with nickname, ping and coins.
    * The coin count lives here (not on the HUD) so each balance is visible.
    */
   private static void renderPlayerList() {
      if (!NetSession.isActive()) {
         return;
      }

      ArrayList<NetSession.PlayerInfo> players = NetSession.getPlayerInfo();
      int width = 320;
      int rowHeight = 26;
      int x = Display.getWidth() - width - 60;
      int y = Display.getHeight() / 2 - 160;
      int height = 44 + rowHeight * Math.max(players.size(), 1) + 10;

      GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.75F);
      Button.drawBackground(x, y, width, height);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

      FontRenderer.saveFontFamily();
      FontRenderer.setFontFamily(FontFamily.ECCENTRIC);
      FontRenderer.draw(x + 14, y + 12, "Players (" + players.size() + "/" + NetSession.MAX_PLAYERS + ")", 0.5F);

      FontRenderer.setFontFamily(FontFamily.CHAPARRAL);
      int rowY = y + 40;
      for (int i = 0; i < players.size(); i++) {
         NetSession.PlayerInfo info = players.get(i);
         // Highlight your own row so it is easy to find.
         if (info.self) {
            GL11.glColor4f(1.0F, 0.95F, 0.6F, 1.0F);
         } else {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.9F);
         }

         FontRenderer.draw(x + 14, rowY, info.nickname + (info.host ? " (host)" : ""), 0.45F);

         String coins = "coins: " + info.money;
         FontRenderer.draw(x + width - 110 - FontRenderer.getTextWidth(coins, 0.45F), rowY, coins, 0.45F);

         String ping = info.pingMs < 0 ? "-" : info.pingMs + "ms";
         FontRenderer.draw(x + width - 14 - FontRenderer.getTextWidth(ping, 0.45F), rowY, ping, 0.45F);
         rowY += rowHeight;
      }

      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      FontRenderer.restoreFontFamily();
   }

   @Override
   public final void onButtonClicked(Button button) {
      if (button.hasLabel("Resume")) {
         Main.gameState = GameState.PLAYING;
      }

      if (button.hasLabel("Options")) {
         MenuController.currentMenuState = MenuState.OPTIONS;
      }

      if (button.hasLabel("Save & Quit")) {
         GameScene.save();
         Main.gameState = GameState.LOADING_MENU;
         MenuController.currentMenuState = MenuState.MAIN;
      }
   }
}
