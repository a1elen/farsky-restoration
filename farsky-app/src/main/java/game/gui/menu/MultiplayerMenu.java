package game.gui.menu;

import game.Main;
import game.enemy.EnemyGenerator;
import game.gui.util.Button;
import game.gui.util.ButtonType;
import game.input.RawInput;
import game.manager.GameMode;
import game.manager.GameScene;
import game.manager.GameState;
import game.manager.Loading;
import game.net.NetSession;
import game.saving.SaveManager;
import game.util.FontFamily;
import game.util.FontRenderer;
import game.world.gen.SeedInput;

import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

public final class MultiplayerMenu extends MenuScreen {
   private Button cancelButton;
   private boolean keyHandled = false;

   public MultiplayerMenu() {
      this.refreshLayout();
   }

   @Override
   protected final void draw() {
      for (int i = 0; i < this.buttons.size(); i++) {
         this.buttons.get(i).render();
      }

      this.cancelButton.render();
      FontRenderer.setFontFamily(FontFamily.CHAPARRAL);
      GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.7F);
      FontRenderer.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2 - 170, "Multiplayer", 0.7F);
      FontRenderer.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2 + 45, "Address: " + NetSession.joinAddress + "_", 0.5F);
      FontRenderer.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2 + 80, NetSession.statusText, 0.5F);

      if (NetSession.status == NetSession.Status.OFFLINE || NetSession.status == NetSession.Status.ERROR) {
         GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.45F);
         FontRenderer.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2 + 115, "Type digits and dots to edit the address", 0.4F);
         FontRenderer.drawCentered(Display.getWidth() / 2, Display.getHeight() / 2 + 140, "The host does not need to type anything", 0.4F);
      }
   }

   @Override
   protected final void update(float delta) {
      super.update(delta);
      this.cancelButton.update(delta);

      if (this.cancelButton.isClicked()) {
         this.onButtonClicked(this.cancelButton);
      }

      this.processAddressKeys();
   }

   @Override
   protected final void onButtonClicked(Button button) {
      if (button.hasLabel("Host game")) {
         if (NetSession.status == NetSession.Status.OFFLINE || NetSession.status == NetSession.Status.ERROR) {
            startHostSession();
         }
      }

      if (button.hasLabel("Join game")) {
         if (NetSession.status == NetSession.Status.OFFLINE || NetSession.status == NetSession.Status.ERROR) {
            String address = NetSession.joinAddress;
            NetSession.join(address.isEmpty() ? "127.0.0.1" : address);
         }
      }

      if (button.hasLabel("Cancel")) {
         NetSession.disconnect();
         MenuController.currentMenuState = MenuState.MAIN;
      }
   }

   /**
    * Host flow: fresh adventure world with a random seed, no intro cinematic
    * (the joined player would only wait for it), and a listening server.
    * Also invoked by the {@code -mpHost} command line flag.
    */
   public static void startHostSession() {
      SeedInput.randomize();
      GameScene.gameMode = GameMode.ADVENTURE;
      SaveManager.generateSavePath();
      Loading.skipCinematic = true;
      Loading.newWorld(10.0F, 7.0F, EnemyGenerator.SpawningLevel.NORMAL);
      NetSession.host();
      Main.gameState = GameState.LOADING_GAME;
   }

   @Override
   public final void refreshLayout() {
      this.buttons.clear();
      this.buttons.add(new Button("Host game", Display.getWidth() / 2, Display.getHeight() / 2 - 120, 350.0F, 60.0F, FontFamily.ECCENTRIC));
      this.buttons.add(new Button("Join game", Display.getWidth() / 2, Display.getHeight() / 2 - 40, 350.0F, 60.0F, FontFamily.ECCENTRIC));
      this.cancelButton = new Button("Cancel", Display.getWidth() / 2, Display.getHeight() - 100, ButtonType.ACTION_BUTTON);
   }

   private void processAddressKeys() {
      if (NetSession.status == NetSession.Status.CONNECTING || NetSession.status == NetSession.Status.LISTENING) {
         this.keyHandled = RawInput.getFirstPressedKey() != -1;
         return;
      }

      int key = RawInput.getFirstPressedKey();
      if (key == -1) {
         this.keyHandled = false;
         return;
      }

      if (this.keyHandled) {
         return;
      }

      this.keyHandled = true;
      String address = NetSession.joinAddress;

      if (key == 14) {
         if (address.length() > 0) {
            NetSession.joinAddress = address.substring(0, address.length() - 1);
         }

         return;
      }

      int digit = -1;
      switch (key) {
         case 2:
            digit = 1;
            break;
         case 3:
            digit = 2;
            break;
         case 4:
            digit = 3;
            break;
         case 5:
            digit = 4;
            break;
         case 6:
            digit = 5;
            break;
         case 7:
            digit = 6;
            break;
         case 8:
            digit = 7;
            break;
         case 9:
            digit = 8;
            break;
         case 10:
            digit = 9;
            break;
         case 11:
            digit = 0;
            break;
         case 52:
            digit = 10;
            break;
         default:
            break;
      }

      if (digit >= 0 && digit <= 9 && address.length() < 15) {
         NetSession.joinAddress = address + digit;
      } else if (digit == 10 && address.length() < 15 && !address.isEmpty() && !address.endsWith(".")) {
         NetSession.joinAddress = address + ".";
      }
   }
}
