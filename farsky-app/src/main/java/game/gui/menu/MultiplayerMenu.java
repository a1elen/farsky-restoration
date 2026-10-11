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

/**
 * Multiplayer browser in the default game-menu style: nickname and address are
 * clickable buttons edited from the keyboard, three world slot buttons host a
 * session directly and one button joins an address. The host owns every save
 * file; the client keeps none (its progress travels through the host).
 */
public final class MultiplayerMenu extends MenuScreen {
   private static final int SLOT_COUNT = 3;
   private static final int FOCUS_NONE = -1;
   private static final int FOCUS_NICK = 0;
   private static final int FOCUS_ADDR = 1;

   private Button nicknameButton;
   private Button addressButton;
   private final Button[] slotButtons = new Button[SLOT_COUNT];
   private Button joinButton;
   private Button cancelButton;
   private final String[] slotInfo = new String[SLOT_COUNT];
   private boolean keyHandled = false;
   private int focus = FOCUS_NONE;
   private String menuStatus = "";
   private float slotInfoTimer = 0.0F;
   // Two-step delete: clicking the X arms the slot, a second click confirms.
   private int armedDeleteSlot = -1;
   private float armedDeleteTimer = 0.0F;
   private static final float DELETE_BOX_SIZE = 26.0F;

   /** Save slot path for a 1-based slot number; the choice seeds the session save. */
   public static String getSlotPath(int slot) {
      return Main.dataPath + "save/world" + slot + ".sav";
   }

   public MultiplayerMenu() {
      SaveManager.loadOptions();
      this.refreshSlotInfo();
      this.refreshLayout();
   }

   @Override
   protected final void draw() {
      for (int i = 0; i < this.buttons.size(); i++) {
         this.buttons.get(i).render();
      }

      this.cancelButton.render();
      int centerX = Display.getWidth() / 2;
      int centerY = Display.getHeight() / 2;
      FontRenderer.setFontFamily(FontFamily.CHAPARRAL);
      GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.7F);
      FontRenderer.drawCentered(centerX, centerY - 195, "Multiplayer", 0.7F);

      for (int i = 0; i < SLOT_COUNT; i++) {
         if (this.slotInfo[i] == null || this.slotInfo[i].equals("Empty")) {
            continue;
         }

         int boxX = this.deleteBoxX(i);
         int boxY = this.deleteBoxY();
         boolean hovered = this.isMouseOverDeleteBox(i);
         boolean armed = this.armedDeleteSlot == i;
         if (armed || hovered) {
            GL11.glColor4f(0.8F, 0.15F, 0.15F, 0.9F);
         } else {
            GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.7F);
         }

         Button.drawBackground(boxX, boxY, DELETE_BOX_SIZE, DELETE_BOX_SIZE);
         GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         FontRenderer.drawCentered(boxX + (int)DELETE_BOX_SIZE / 2, boxY + 5, "X", 0.5F);
         if (armed) {
            GL11.glColor4f(1.0F, 0.4F, 0.4F, 1.0F);
            FontRenderer.drawCentered(boxX + (int)DELETE_BOX_SIZE / 2, boxY + 34, "Sure?", 0.4F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
         }
      }

      String status = this.menuStatus.isEmpty() ? NetSession.statusText : this.menuStatus;
      if (status != null && !status.isEmpty()) {
         GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.8F);
         FontRenderer.drawCentered(centerX, centerY + 120, status, 0.5F);
      }

      if (NetSession.status == NetSession.Status.OFFLINE || NetSession.status == NetSession.Status.ERROR) {
         GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.45F);
         String hint;
         if (this.focus == FOCUS_NICK) {
            hint = "Type to edit - Backspace deletes";
         } else if (this.focus == FOCUS_ADDR) {
            hint = "Type digits and dots to edit the address";
         } else {
            hint = "Click Nickname or Address to edit them";
         }

         FontRenderer.drawCentered(centerX, centerY + 150, hint, 0.4F);
         FontRenderer.drawCentered(centerX, centerY + 172, "The host clicks a world slot to start", 0.4F);
      }
   }

   @Override
   protected final void update(float delta) {
      this.slotInfoTimer += delta;
      if (this.slotInfoTimer >= 2.0F) {
         this.slotInfoTimer = 0.0F;
         this.refreshSlotInfo();
      }

      this.refreshLabels();
      super.update(delta);
      this.cancelButton.update(delta);

      if (this.cancelButton.isClicked()) {
         this.onButtonClicked(this.cancelButton);
      }

      this.processDeleteClicks(delta);
      this.processKeys();
   }

   @Override
   protected final void onButtonClicked(Button button) {
      if (button == this.nicknameButton) {
         this.focus = FOCUS_NICK;
         this.keyHandled = RawInput.getFirstPressedKey() != -1;
         return;
      }

      if (button == this.addressButton) {
         this.focus = FOCUS_ADDR;
         this.keyHandled = RawInput.getFirstPressedKey() != -1;
         return;
      }

      if (button == this.joinButton) {
         this.startSessionForJoin();
         return;
      }

      for (int i = 0; i < SLOT_COUNT; i++) {
         if (button == this.slotButtons[i]) {
            this.startSessionForSlot(i + 1);
            return;
         }
      }

      if (button == this.cancelButton) {
         NetSession.disconnect();
         MenuController.currentMenuState = MenuState.MAIN;
      }
   }

   /**
    * Host flow: load the chosen world slot (or create a fresh adventure world
    * with a random seed when the slot is empty), skip the intro cinematic (the
    * joined player would only wait for it) and start listening for a client.
    * Slot overload is used by the menu buttons, no-arg by {@code -mpHost}.
    */
   public static void startHostSession(int slot) {
      String slotPath = getSlotPath(slot);
      Loading.skipCinematic = true;
      if (new java.io.File(slotPath).exists()) {
         game.saving.SaveSlot saveSlot = new game.saving.SaveSlot(slotPath);
         if (!saveSlot.isEmpty()) {
            saveSlot.load();
            NetSession.host();
            Main.gameState = GameState.LOADING_GAME;
            return;
         }
      }

      SeedInput.randomize();
      GameScene.gameMode = GameMode.ADVENTURE;
      SaveManager.setSavePath(slotPath);
      Loading.skipCinematic = true;
      Loading.newWorld(10.0F, 7.0F, EnemyGenerator.SpawningLevel.NORMAL);
      NetSession.host();
      Main.gameState = GameState.LOADING_GAME;
   }

   /** Command line flag {@code -mpHost}: world slot 1. */
   public static void startHostSession() {
      startHostSession(1);
   }

   @Override
   public final void refreshLayout() {
      int centerX = Display.getWidth() / 2;
      int centerY = Display.getHeight() / 2;
      this.buttons.clear();
      this.nicknameButton = new Button("Nickname", centerX, centerY - 150, ButtonType.ACTION_BUTTON);
      this.addressButton = new Button("Address", centerX, centerY - 95, ButtonType.ACTION_BUTTON);
      this.slotButtons[0] = new Button("Slot 1", centerX - 300, centerY - 25, ButtonType.ACTION_BUTTON);
      this.slotButtons[1] = new Button("Slot 2", centerX, centerY - 25, ButtonType.ACTION_BUTTON);
      this.slotButtons[2] = new Button("Slot 3", centerX + 300, centerY - 25, ButtonType.ACTION_BUTTON);
      this.joinButton = new Button("Join game", centerX, centerY + 55, ButtonType.ACTION_BUTTON);
      this.cancelButton = new Button("Cancel", centerX, Display.getHeight() - 100, ButtonType.ACTION_BUTTON);
      this.buttons.add(this.nicknameButton);
      this.buttons.add(this.addressButton);
      this.buttons.add(this.slotButtons[0]);
      this.buttons.add(this.slotButtons[1]);
      this.buttons.add(this.slotButtons[2]);
      this.buttons.add(this.joinButton);
      this.refreshLabels();
   }

   /** Dynamic labels: current values (with a caret when focused) and slots. */
   private void refreshLabels() {
      if (this.nicknameButton == null) {
         return;
      }

      this.nicknameButton.setLabel("Nickname: " + NetSession.nickname + (this.focus == FOCUS_NICK ? "_" : ""));
      this.addressButton.setLabel("Address: " + NetSession.joinAddress + (this.focus == FOCUS_ADDR ? "_" : ""));
      for (int i = 0; i < SLOT_COUNT; i++) {
         this.slotButtons[i].setLabel("Slot " + (i + 1) + ": " + this.slotInfo[i]);
      }
   }

   /** Validates the nickname: sanitized, trimmed and never empty / "Player". */
   private boolean validateNickname() {
      NetSession.nickname = NetSession.sanitizeNickname(NetSession.nickname);
      if (NetSession.nickname.equalsIgnoreCase("Player")) {
         this.menuStatus = "Enter a nickname (not 'Player')";
         return false;
      }

      SaveManager.saveOption("Nickname", NetSession.nickname);
      this.menuStatus = "";
      return true;
   }

   private void startSessionForSlot(int slot) {
      if (NetSession.status != NetSession.Status.OFFLINE && NetSession.status != NetSession.Status.ERROR) {
         this.menuStatus = "Connection already active - press Cancel first";
         return;
      }

      if (!this.validateNickname()) {
         return;
      }

      startHostSession(slot);
   }

   private void startSessionForJoin() {
      if (NetSession.status != NetSession.Status.OFFLINE && NetSession.status != NetSession.Status.ERROR) {
         this.menuStatus = "Connection already active - press Cancel first";
         return;
      }

      if (!this.validateNickname()) {
         return;
      }

      String address = NetSession.joinAddress;
      NetSession.join(address.isEmpty() ? "127.0.0.1" : address);
   }

   /**
    * Focus-driven key handling: TAB switches the nickname and address fields.
    * Nickname edits come from RawInput.typedChars (letters, digits, Cyrillic).
    */
   private void processKeys() {
      if (NetSession.status == NetSession.Status.CONNECTING || NetSession.status == NetSession.Status.LISTENING) {
         this.keyHandled = RawInput.getFirstPressedKey() != -1;
         RawInput.typedChars.clear();
         return;
      }

      int key = RawInput.getFirstPressedKey();
      if (key == 15 && !this.keyHandled) {
         this.keyHandled = true;
         this.focus = this.focus == FOCUS_NICK ? FOCUS_ADDR : FOCUS_NICK;
      } else if (this.focus == FOCUS_NICK) {
         this.processNicknameKeys(key);
      } else if (this.focus == FOCUS_ADDR) {
         this.processAddressKeys(key);
      } else if (key == -1) {
         this.keyHandled = false;
      }

      if (this.focus == FOCUS_NICK) {
         for (int i = 0; i < RawInput.typedChars.size(); i++) {
            char c = RawInput.typedChars.get(i);
            if (c >= ' ' && NetSession.nickname.length() < 16) {
               NetSession.nickname = NetSession.nickname + c;
               this.menuStatus = "";
            }
         }

         if (RawInput.typedChars.size() > 0) {
            this.saveNickname();
         }
      }

      RawInput.typedChars.clear();
   }

   private void processNicknameKeys(int key) {
      if (key == -1) {
         this.keyHandled = false;
         return;
      }

      if (this.keyHandled) {
         return;
      }

      this.keyHandled = true;
      if (key == 14 && NetSession.nickname.length() > 0) {
         NetSession.nickname = NetSession.nickname.substring(0, NetSession.nickname.length() - 1);
         this.menuStatus = "";
         this.saveNickname();
      }
   }

   private void processAddressKeys(int key) {
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
            this.menuStatus = "";
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
         this.menuStatus = "";
      } else if (digit == 10 && address.length() < 15 && !address.isEmpty() && !address.endsWith(".")) {
         NetSession.joinAddress = address + ".";
         this.menuStatus = "";
      }
   }

   private void refreshSlotInfo() {
      for (int i = 0; i < SLOT_COUNT; i++) {
         String path = getSlotPath(i + 1);
         game.saving.SlotPresentation info = null;
         if (new java.io.File(path).exists()) {
            info = SaveManager.readSlotPresentation(path);
         }

         this.slotInfo[i] = info == null ? "Empty" : info.getGameMode() + ", " + info.getMinutesPlayed() + " min";
      }
   }

   /** Left edge of the delete box for a slot: right of the slot button, in the gap. */
   private int deleteBoxX(int slot) {
      return Display.getWidth() / 2 - 300 + 300 * slot + 115;
   }

   private int deleteBoxY() {
      return Display.getHeight() / 2 - 38;
   }

   private boolean isMouseOverDeleteBox(int slot) {
      int boxX = this.deleteBoxX(slot);
      int boxY = this.deleteBoxY();
      return RawInput.mouseX > boxX
         && RawInput.mouseX < boxX + DELETE_BOX_SIZE
         && RawInput.mouseY > boxY
         && RawInput.mouseY < boxY + DELETE_BOX_SIZE;
   }

   /**
    * First click on a slot's X arms the delete ("Sure?"), the second one within
    * ~3 seconds deletes the world save and every player progress file of that
    * world. Clicking anywhere else disarms.
    */
   private void processDeleteClicks(float delta) {
      if (this.armedDeleteSlot >= 0) {
         this.armedDeleteTimer -= delta;
         if (this.armedDeleteTimer <= 0.0F) {
            this.armedDeleteSlot = -1;
         }
      }

      if (!RawInput.leftMouseDown) {
         return;
      }

      for (int i = 0; i < SLOT_COUNT; i++) {
         if (this.slotInfo[i] == null || this.slotInfo[i].equals("Empty") || !this.isMouseOverDeleteBox(i)) {
            continue;
         }

         if (this.armedDeleteSlot == i) {
            this.deleteSlot(i);
         } else {
            this.armedDeleteSlot = i;
            this.armedDeleteTimer = 3.0F;
         }

         return;
      }

      this.armedDeleteSlot = -1;
   }

   /** Removes the world save plus its save/players/&lt;world&gt;_*.sav progress files. */
   private void deleteSlot(int slot) {
      this.armedDeleteSlot = -1;
      SaveManager.deleteSave(getSlotPath(slot + 1));
      java.io.File playersDir = new java.io.File(Main.dataPath + "save/players");
      java.io.File[] playerFiles = playersDir.listFiles();
      if (playerFiles != null) {
         String prefix = "world" + (slot + 1) + "_";
         for (int i = 0; i < playerFiles.length; i++) {
            if (playerFiles[i].getName().startsWith(prefix)) {
               playerFiles[i].delete();
            }
         }
      }

      this.menuStatus = "World slot " + (slot + 1) + " deleted";
      this.refreshSlotInfo();
   }

   private void saveNickname() {
      SaveManager.saveOption("Nickname", NetSession.nickname);
   }
}
