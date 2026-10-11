package game.saving;

import game.Main;
import game.environment.DepthAtmosphere;
import game.gui.dialog.DialogManager;
import game.gui.menu.OptionsMenu;
import game.inventory.types.PlayerInventory;
import game.manager.Achievements;
import game.manager.GameMode;
import game.manager.InGameState;
import game.manager.Loading;
import game.manager.Stats;
import game.manager.GameScene;
import game.manager.GameTime;
import game.outsideObj.OutsideObj;
import game.player.Avatar;
import game.player.PlayerState;
import game.player.droid.Droid;
import game.seafloorBase.SeafloorBase;
import game.submarine.Submarine;
import game.world.World;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

public final class SaveManager {
   private static String currentSavePath = "";
   private static final ArrayList<String> cachedOptionKeys = new ArrayList<>();
   private static final ArrayList<String> cachedOptionValues = new ArrayList<>();
   private static boolean optionsCached = false;

   public static synchronized void saveGame() {
      if (GameScene.avatar != null) {
         SlotPresentation presentation = new SlotPresentation((int)DepthAtmosphere.getDepthInMeters(), (int)(GameTime.totalPlayTime / 60.0F), (int)(GameTime.dayTime / 60.0F), GameScene.gameMode, GameScene.avatar.getSubmarinePiecesCount());

         try {
            new File(Main.dataPath + "save").mkdir();
            FileOutputStream fos = new FileOutputStream(currentSavePath + ".tmp");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(presentation);
            oos.writeObject(Main.VERSION);
            oos.writeObject(GameScene.gameMode);
            oos.writeObject(GameScene.avatar.getPlayerState());
            oos.writeObject(GameScene.avatar.getInventory());
            oos.writeObject(Loading.getPendingWorld());
            if (GameScene.getSeafloorBases() != null) {
               for (int i = 0; i < GameScene.getSeafloorBases().size(); i++) {
                  oos.writeObject(GameScene.getSeafloorBases().get(i));
               }
            }

            if (GameScene.getOutsideObjects() != null) {
               for (int i = 0; i < GameScene.getOutsideObjects().size(); i++) {
                  oos.writeObject(GameScene.getOutsideObjects().get(i));
               }
            }

            if (GameScene.getSubmarines() != null) {
               for (int i = 0; i < GameScene.getSubmarines().size(); i++) {
                  oos.writeObject(GameScene.getSubmarines().get(i));
               }
            }

            if (GameScene.getDroids() != null) {
               for (int i = 0; i < GameScene.getDroids().size(); i++) {
                  oos.writeObject(GameScene.getDroids().get(i));
               }
            }

            if (GameScene.dialogManager != null) {
               oos.writeObject(GameScene.dialogManager);
            }

            if (GameScene.getInGameState() != null) {
               oos.writeObject(GameScene.getInGameState());
            }

            if (GameScene.stats != null) {
               oos.writeObject(GameScene.stats);
            }

            oos.close();
            fos.close();
            File tmpFile = new File(currentSavePath + ".tmp");
            File saveFile = new File(currentSavePath);
            saveFile.delete();
            tmpFile.renameTo(saveFile);
            if (Main.isVerbose) {
               System.out.println("Saved: " + currentSavePath);
            }

            return;
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
   }

   private static void closeStreams(ObjectInputStream ois, FileInputStream fis) {
      try {
         if (ois != null) {
            ois.close();
         }

         if (fis != null) {
            fis.close();
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void loadGame(String path) {
      currentSavePath = path;
      FileInputStream fis = null;
      ObjectInputStream ois = null;
      PlayerInventory inventory = null;
      PlayerState playerState = null;

      try {
         fis = new FileInputStream(currentSavePath);
         ois = new ObjectInputStream(fis);
         GameScene.clear();

         Object obj;
         while ((obj = ois.readObject()) != null) {
            if (obj instanceof GameMode) {
               GameScene.gameMode = (GameMode)obj;
            }

            if (obj instanceof PlayerState) {
               playerState = (PlayerState)obj;
            }

            if (obj instanceof PlayerInventory) {
               inventory = (PlayerInventory)obj;
            }

            if (obj instanceof World) {
               Loading.loadGame((World)obj);
            }

            if (obj instanceof SeafloorBase) {
               GameScene.registerSeafloorBase((SeafloorBase)obj);
            }

            if (obj instanceof OutsideObj) {
               GameScene.registerOutsideObject((OutsideObj)obj);
            }

            if (obj instanceof Submarine) {
               GameScene.registerSubmarine((Submarine)obj);
            }

            if (obj instanceof Droid) {
               GameScene.registerDroid((Droid)obj);
            }

            if (obj instanceof DialogManager) {
               GameScene.registerDialogManager((DialogManager)obj);
            }

            if (obj instanceof InGameState) {
               GameScene.setInitialState((InGameState)obj);
            }

            if (obj instanceof Stats) {
               GameScene.registerStats((Stats)obj);
            }
         }
      } catch (EOFException e) {
      } catch (IOException e) {
         e.printStackTrace();
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }

      closeStreams(ois, fis);

      GameScene.registerAvatar(new Avatar(playerState, inventory));
      if (Main.isVerbose) {
         System.out.println("Loaded: " + currentSavePath);
      }
   }

   public static SlotPresentation readSlotPresentation(String path) {
      SlotPresentation slot = null;

      try {
         FileInputStream fis = new FileInputStream(path);
         ObjectInputStream ois = new ObjectInputStream(fis);
         slot = (SlotPresentation)ois.readObject();
         Object obj = ois.readObject();
         if (obj instanceof String && ((String)obj).contains("Beta")) {
            slot = null;
         }

         ois.close();
         fis.close();
      } catch (IOException e) {
         e.printStackTrace();
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }

      return slot;
   }

   public static void generateSavePath() {
      currentSavePath = Main.dataPath + "save/farsky" + System.currentTimeMillis() / 1000L + ".sav";
      if (Main.isVerbose) {
         System.out.println(currentSavePath);
      }
   }

   /** Points the next saveGame() at a fixed path (multiplayer world slots). */
   public static void setSavePath(String path) {
      currentSavePath = path;
      if (Main.isVerbose) {
         System.out.println(currentSavePath);
      }
   }

   /** Header magic for client progress blobs stored by the host. */
   private static final int CLIENT_STATE_MAGIC = 0x46534331;

   /**
    * Serializes the local player's progress (world seed, position, inventory).
    * The client hands this to the host, which stores it in its player file.
    * Returns null when there is nothing to serialize.
    */
   public static byte[] createClientState() {
      if (GameScene.avatar == null) {
         return null;
      }

      try {
         java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(512);
         ObjectOutputStream oos = new ObjectOutputStream(bos);
         oos.writeInt(CLIENT_STATE_MAGIC);
         oos.writeInt(game.world.gen.SeedInput.getSeed());
         game.util.Point pos = GameScene.avatar.getPos();
         oos.writeFloat(pos.x);
         oos.writeFloat(pos.y);
         oos.writeFloat(pos.z);
         oos.writeObject(GameScene.avatar.getPlayerState());
         oos.writeObject(GameScene.avatar.getInventory());
         // Coins are host-authoritative: stored with the player's progress so a
         // rejoining player gets their balance back.
         oos.writeInt(game.Main.achievements != null ? game.Main.achievements.getMoney() : 0);
         oos.flush();
         return bos.toByteArray();
      } catch (Throwable t) {
         return null;
      }
   }

   /**
    * Applies progress previously stored by the host. Silently ignored when the
    * blob belongs to another world (seed mismatch) or cannot be read.
    */
   public static boolean applyClientState(byte[] data) {
      if (data == null || GameScene.avatar == null) {
         return false;
      }

      try {
         ObjectInputStream ois = new ObjectInputStream(new java.io.ByteArrayInputStream(data));
         if (ois.readInt() != CLIENT_STATE_MAGIC || ois.readInt() != game.world.gen.SeedInput.getSeed()) {
            return false;
         }

         float x = ois.readFloat();
         float y = ois.readFloat();
         float z = ois.readFloat();

         // Newer blobs carry the vitals before the inventory; older files
         // (written before PlayerState was added) start straight with it.
         game.player.PlayerState savedState = null;
         PlayerInventory saved;
         Object first = ois.readObject();
         if (first instanceof game.player.PlayerState) {
            savedState = (game.player.PlayerState)first;
            saved = (PlayerInventory)ois.readObject();
         } else {
            saved = (PlayerInventory)first;
         }

         if (savedState != null) {
            game.player.PlayerState current = GameScene.avatar.getPlayerState();
            current.copyFrom(savedState);
            current.adjustOxygenLevel(savedState.getOxygenLevel() - current.getOxygenLevel(), Float.MAX_VALUE);
         }

         game.util.Point pos = new game.util.Point(x, y, z);
         GameScene.avatar.setPos(pos);
         GameScene.avatar.setLastSafeSpot(pos);
         game.net.NetSession.copyInventoryContents(saved, GameScene.avatar.getInventory());
         try {
            if (game.Main.achievements != null) {
               game.Main.achievements.setMoney(ois.readInt());
            }
         } catch (Throwable oldBlob) {
            // Older progress files end after the inventory: keep current coins.
         }

         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   /**
    * Host side: writes the connected client's progress next to the world file,
    * keyed by nickname (save/players/&lt;world&gt;_&lt;nickname&gt;.sav), so a player
    * keeps their inventory when rejoining the same world.
    */
   public static synchronized void savePlayerProgress(String nickname, byte[] state) {
      String path = playerProgressPath(nickname);
      if (path == null || state == null || state.length == 0) {
         return;
      }

      try {
         new File(Main.dataPath + "save/players").mkdirs();
         FileOutputStream fos = new FileOutputStream(path + ".tmp");
         fos.write(state);
         fos.close();
         File tmpFile = new File(path + ".tmp");
         File saveFile = new File(path);
         saveFile.delete();
         tmpFile.renameTo(saveFile);
         if (Main.isVerbose) {
            System.out.println("Saved player: " + path);
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   /** Host side: reads the client's progress for the current world, or null. */
   public static synchronized byte[] loadPlayerProgress(String nickname) {
      String path = playerProgressPath(nickname);
      if (path == null) {
         return null;
      }

      try {
         File file = new File(path);
         if (!file.exists()) {
            return null;
         }

         return java.nio.file.Files.readAllBytes(file.toPath());
      } catch (Throwable t) {
         return null;
      }
   }

   /** Save file for one player's progress inside the current world (host only). */
   private static String playerProgressPath(String nickname) {
      if (currentSavePath == null || currentSavePath.isEmpty()) {
         return null;
      }

      String world = currentSavePath;
      int slash = Math.max(world.lastIndexOf('/'), world.lastIndexOf('\\'));
      if (slash >= 0) {
         world = world.substring(slash + 1);
      }

      if (world.endsWith(".sav")) {
         world = world.substring(0, world.length() - 4);
      }

      StringBuilder name = new StringBuilder();
      String nick = nickname == null ? "" : nickname;
      for (int i = 0; i < nick.length() && name.length() < 24; i++) {
         char c = nick.charAt(i);
         name.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
      }

      String safe = name.toString();
      if (safe.replace("_", "").isEmpty()) {
         safe = "player";
      }

      return Main.dataPath + "save/players/" + world + "_" + safe + ".sav";
   }

   public static void deleteSave() {
      new File(currentSavePath).delete();
   }

   public static void deleteSave(String path) {
      new File(path).delete();
   }

   public static void saveOptions(ArrayList<String> keys, ArrayList<String> values) {
      cachedOptionKeys.clear();
      cachedOptionValues.clear();
      cachedOptionKeys.addAll(keys);
      cachedOptionValues.addAll(values);
      optionsCached = true;
      try {
         FileOutputStream fos = new FileOutputStream(Main.dataPath + "options.sav.tmp");
         ObjectOutputStream oos = new ObjectOutputStream(fos);

         for (int i = 0; i < keys.size(); i++) {
            oos.writeObject(keys.get(i));
            oos.writeObject(values.get(i));
         }

         oos.close();
         fos.close();
         File tmpFile = new File(Main.dataPath + "options.sav.tmp");
         File optFile = new File(Main.dataPath + "options.sav");
         optFile.delete();
         tmpFile.renameTo(optFile);
         if (Main.isVerbose) {
            System.out.println("Options saved: " + Main.dataPath + "options.sav");
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void loadOptions() {
      cachedOptionKeys.clear();
      cachedOptionValues.clear();
      optionsCached = false;
      FileInputStream fis = null;
      ObjectInputStream ois = null;

      try {
         if (new File(Main.dataPath + "options.sav").exists()) {
            fis = new FileInputStream(Main.dataPath + "options.sav");
            ois = new ObjectInputStream(fis);

            Object obj;
            while ((obj = ois.readObject()) != null) {
               String key = (String)obj;
               obj = ois.readObject();
               if (obj != null) {
                  cachedOptionKeys.add(key);
                  cachedOptionValues.add((String)obj);
                  OptionsMenu.applySetting(key, (String)obj);
               }
            }
         }
      } catch (EOFException e) {
      } catch (IOException e) {
         e.printStackTrace();
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }

      closeStreams(ois, fis);

      if (Main.isVerbose) {
         System.out.println("Loaded Options: " + Main.dataPath + "options.sav");
      }
   }

   /** Updates a single option key without dropping the others, then rewrites the file. */
   public static void saveOption(String key, String value) {
      if (!optionsCached) {
         loadOptions();
      }

      int idx = cachedOptionKeys.indexOf(key);
      if (idx >= 0) {
         cachedOptionValues.set(idx, value);
      } else {
         cachedOptionKeys.add(key);
         cachedOptionValues.add(value);
      }

      saveOptions(cachedOptionKeys, cachedOptionValues);
   }

   public static void saveAchievements() {
      try {
         FileOutputStream fos = new FileOutputStream(Main.dataPath + "achievement.lck.tmp");
         ObjectOutputStream oos = new ObjectOutputStream(fos);
         oos.writeObject(Main.achievements);
         oos.close();
         fos.close();
         File tmpFile = new File(Main.dataPath + "achievement.lck.tmp");
         File achievFile = new File(Main.dataPath + "achievement.lck");
         achievFile.delete();
         tmpFile.renameTo(achievFile);
         if (Main.isVerbose) {
            System.out.println("Achievement saved: " + Main.dataPath + "achievement.lck");
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public static void loadAchievements() {
      FileInputStream fis = null;
      ObjectInputStream ois = null;

      try {
         if (new File(Main.dataPath + "achievement.lck").exists()) {
            fis = new FileInputStream(Main.dataPath + "achievement.lck");
            ois = new ObjectInputStream(fis);

            Object obj;
            while ((obj = ois.readObject()) != null) {
               if (obj instanceof GameMode && (GameMode)obj == GameMode.ADVENTURE) {
                  Main.achievements.unlockSurvivor();
               }

               if (obj instanceof Achievements) {
                  Main.achievements = (Achievements)obj;
               }
            }
         }
      } catch (EOFException e) {
      } catch (IOException e) {
         e.printStackTrace();
      } catch (ClassNotFoundException e) {
         e.printStackTrace();
      }

      closeStreams(ois, fis);

      Main.achievements.updateSurvivorLock();
      if (Main.isVerbose) {
         System.out.println("Read achievement: " + Main.dataPath + "achievement.lck");
      }
   }
}
