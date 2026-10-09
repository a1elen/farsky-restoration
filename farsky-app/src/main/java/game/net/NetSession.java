package game.net;

import game.Main;
import game.chunks.ChunkManager;
import game.enemy.EnemyGenerator;
import game.inventory.Item;
import game.inventory.ItemType;
import game.inventory.StorageArray;
import game.inventory.types.Inventory;
import game.manager.GameMode;
import game.manager.GameScene;
import game.manager.GameState;
import game.manager.GameTime;
import game.manager.Loading;
import game.saving.SaveManager;
import game.seafloorBase.SeafloorBase;
import game.util.Coord;
import game.util.Point;
import game.util.Segment;
import game.world.World;
import game.world.WorldManager;
import game.world.gen.SeedInput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.nio.FloatBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Minimal TCP session for two players (host + client).
 *
 * <p>Protocol: frames of {@code int length} + {@code byte[length] payload},
 * first payload byte is the message type:</p>
 * <ul>
 *   <li>{@link #MSG_HELLO}    client -> host: protocol version and nickname</li>
 *   <li>{@link #MSG_WELCOME}  host -> client: world seed and settings, so the
 *       client can deterministically rebuild the same world</li>
 *   <li>{@link #MSG_POS}      both: player position and yaw at 20 Hz</li>
   *   <li>{@link #MSG_PLAYER_STATE} client -> host: progress snapshot that the
   *       host stores in its player file and replays on the next join</li>
 * </ul>
 *
 * <p>All game state changes happen on the game thread inside {@link #update(float)};
 * network threads only move bytes and set volatile flags.</p>
 */
public final class NetSession {
   public enum Role { NONE, HOST, CLIENT }
   public enum Status { OFFLINE, LISTENING, CONNECTING, CONNECTED, ERROR }

   public static final int DEFAULT_PORT = 45678;
   private static final int PROTOCOL_VERSION = 7;
   private static final byte MSG_HELLO = 1;
   private static final byte MSG_WELCOME = 2;
   private static final byte MSG_POS = 3;
   private static final byte MSG_CHEST_TAKEN = 4;
   private static final byte MSG_PLANT_HARVEST = 5;
   private static final byte MSG_ORE_DEPOSIT = 6;
   private static final byte MSG_ORE_MINED = 7;
   private static final byte MSG_BASE_SPAWN = 8;
   private static final byte MSG_BLOCK_OP = 9;
   private static final byte MSG_ITEM_SPAWN = 10;
   private static final byte MSG_ITEM_TAKE = 11;
   private static final byte MSG_CHEST_INVENTORY = 12;
   private static final byte MSG_BASE_INVENTORY = 13;
   private static final byte MSG_CHAT = 14;
   private static final byte MSG_TOMB_SPAWN = 15;
   private static final byte MSG_TOMB_INVENTORY = 16;
   private static final byte MSG_OBJ_SPAWN = 17;
   private static final byte MSG_SUB_SPAWN = 18;
   private static final byte MSG_OBJ_INVENTORY = 19;
   private static final byte MSG_DROID_SPAWN = 20;
   private static final byte MSG_DROID_INVENTORY = 21;
   private static final byte MSG_DROID_STATE = 22;
   private static final byte MSG_DROID_SYNC = 23;
   private static final byte MSG_WATER = 24;
   private static final byte MSG_POT_STATE = 25;
   private static final byte MSG_SUB_PIECE = 26;
   /** Client -> host: latest client progress (position + inventory) for host-side storage. */
   private static final byte MSG_PLAYER_STATE = 27;

   /** Host -> client: authoritative clock + light level (day/night sync). */
   private static final byte MSG_TIME = 28;
   /** Host -> client: batched creature states; unknown ids create the creature. */
   private static final byte MSG_ENEMY_SYNC = 29;
   /** Host -> client: the creature with this id left the world. */
   private static final byte MSG_ENEMY_DESPAWN = 30;
   /** Client -> host: damage the client just applied to a mirrored creature. */
   private static final byte MSG_ENEMY_HIT = 31;
   /** Host -> client: batched fish states; unknown ids create the fish. */
   private static final byte MSG_FISH_SYNC = 32;
   /** Host -> client: the fish with this id left the world. */
   private static final byte MSG_FISH_DESPAWN = 33;
   /** Client -> host: damage the client just applied to a mirrored fish. */
   private static final byte MSG_FISH_HIT = 34;
   // Kinds carried by MSG_OBJ_SPAWN (placed outside objects).
   public static final int OBJ_EXTRACTOR = 0;
   public static final int OBJ_EXTRACTOR_OVERPOWERED = 1;
   public static final int OBJ_HARPOON = 2;
   public static final int OBJ_LAMP = 3;
   private static final int MAX_FRAME_SIZE = 33554432;
   private static final float SEND_INTERVAL = 0.05F;
   private static final float WATER_SEND_INTERVAL = 2.0F;
   private static final float DROID_SEND_INTERVAL = 0.2F;
   private static final float CREATURE_SEND_INTERVAL = 0.1F;
   /** Creatures packed into a single MSG_ENEMY_SYNC frame. */
   private static final int CREATURE_BATCH = 128;
   private static final float FISH_SEND_INTERVAL = 0.34F;
   /** Fish packed into a single MSG_FISH_SYNC frame. */
   private static final int FISH_BATCH = 160;
   private static final float PLAYER_STATE_INTERVAL = 2.5F;
   private static final int CONNECT_TIMEOUT_MS = 5000;

   // Block operation codes for MSG_BLOCK_OP.
   public static final int OP_BUILD = 0;
   public static final int OP_CLEAR = 1;
   public static final int OP_DEMOLISH = 2;
   public static final int OP_ADD_ELEMENT = 3;
   public static final int OP_REMOVE_ELEMENT = 4;
   public static final int OP_APPLY_MATERIAL = 5;

   /** Current role of this instance. NONE means no session. */
   public static volatile Role role = Role.NONE;
   /** Connection status shown in the multiplayer menu. */
   public static volatile Status status = Status.OFFLINE;
   /** Human readable status line for the menu. */
   public static volatile String statusText = "";
   /** This player's nickname; shown to the peer and persisted via options.sav. */
   public static volatile String nickname = "Player";
   /** The peer's nickname received during the handshake. */
   public static volatile String remoteNickname = "Player";
   /** Screen position of the remote player's nametag (set during the 3D pass). */
   private static float nametagX = 0.0F;
   private static float nametagY = 0.0F;
   private static boolean nametagVisible = false;

   public static int port = DEFAULT_PORT;
   public static String joinAddress = "127.0.0.1";

   private static volatile boolean running = false;
   private static volatile boolean clientAlive = false;
   private static volatile boolean eventClientJoined = false;
   private static volatile boolean eventLinkLost = false;

   private static volatile ServerSocket serverSocket;
   private static volatile Socket activeSocket;
   private static volatile DataOutputStream activeOut;
   private static volatile Thread writerThread = null;

   private static final ConcurrentLinkedQueue<byte[]> incoming = new ConcurrentLinkedQueue<byte[]>();
   private static final ConcurrentLinkedQueue<byte[]> outgoing = new ConcurrentLinkedQueue<byte[]>();

   private static RemotePlayer remote = null;
   private static boolean welcomed = false;
   private static boolean pendingSpawn = false;
   private static byte[] pendingPieces = null;
   /** Client: progress blob from the welcome, applied after the avatar spawns. */
   private static byte[] pendingPlayerState = null;
   /** Host: latest progress received from the client, written on save/leave. */
   private static byte[] bufferedClientState = null;
   private static boolean handshakeDone = false;
   private static float playerStateTimer = 0.0F;
   private static final FloatBuffer projModel = BufferUtils.createFloatBuffer(16);
   private static final FloatBuffer projProj = BufferUtils.createFloatBuffer(16);
   private static final FloatBuffer projViewport = BufferUtils.createFloatBuffer(16);
   private static float sendTimer = 0.0F;
   private static boolean welcomeSent = false;
   private static boolean applyingRemote = false;
   private static int suppressSends = 0;
   private static final ArrayList<byte[]> pendingWorldOps = new ArrayList<byte[]>();
   // Last chest inventory snapshot sent or received, to avoid resending unchanged chests.
   private static int chestSyncTileX = -1;
   private static int chestSyncTileZ = -1;
   private static byte[] chestSyncBytes = null;
   // Same for base element inventories (workshops, cookers, base chests).
   private static int baseSyncIdx = -1;
   private static int baseSyncX = -1;
   private static int baseSyncY = -1;
   private static int baseSyncZ = -1;
   private static int baseSyncType = -1;
   private static byte[] baseSyncBytes = null;
   // Last death tomb inventory snapshot sent or received.
   private static byte[] tombSyncBytes = null;
   // Last outside-object (extractor) inventory snapshot sent or received.
   private static float objSyncX = Float.NaN;
   private static float objSyncZ = Float.NaN;
   private static byte[] objSyncBytes = null;
   // Last droid inventory snapshot sent or received.
   private static int droidSyncIdx = -1;
   private static byte[] droidSyncBytes = null;
   // Peer state mirrored from MSG_POS (money, driven submarine).
   private static int remoteMoney = 0;
   private static int remoteSubIdx = -1;
   // Periodic host snapshots: water levels, droid positions, creatures.
   private static float waterTimer = 0.0F;
   private static float droidTimer = 0.0F;
   private static float creatureTimer = 0.0F;
   /** Host: creature ids the client already mirrors; used to detect removals. */
   private static final java.util.HashSet<Integer> knownEnemies = new java.util.HashSet<Integer>();
   private static float fishTimer = 0.0F;
   private static int nextFishId = 1;
   /** Host: fish ids the client already mirrors; used to detect removals. */
   private static final java.util.HashSet<Integer> knownFish = new java.util.HashSet<Integer>();
   private static final java.util.ArrayList<game.environment.life.Fish> fishSyncScratch =
         new java.util.ArrayList<game.environment.life.Fish>();
   private static final java.util.ArrayList<game.environment.life.Fish> fishEligible =
         new java.util.ArrayList<game.environment.life.Fish>();

   static {
      String addr = System.getProperty("farsky.join");
      if (addr != null && !addr.isEmpty()) {
         joinAddress = addr;
      }

      String portValue = System.getProperty("farsky.port");
      if (portValue != null) {
         try {
            port = Integer.parseInt(portValue);
         } catch (NumberFormatException e) {
            port = DEFAULT_PORT;
         }
      }
   }

   private NetSession() {
   }

   public static boolean isActive() {
      return role != Role.NONE;
   }

   /** The peer's coin count, mirrored from MSG_POS (0 when unknown). */
   public static int getRemoteMoney() {
      return remoteMoney;
   }

   /**
    * Host: position of the joined player as a chase target, or null when no
    * one is connected. Lets the host's creatures hunt both players instead of
    * only the host's own avatar.
    */
   public static Point getRemoteAggroPos() {
      if (role != Role.HOST || status != Status.CONNECTED || remote == null) {
         return null;
      }

      return remote.getRenderPos();
   }

   /** True while this instance joins someone else's session as the client. */
   public static boolean isClient() {
      return role == Role.CLIENT;
   }

   public static boolean isClientMirror() {
      return role == Role.CLIENT && status == Status.CONNECTED && isGameplayState();
   }

   /** Starts listening for one client and creates a fresh adventure world. */
   public static synchronized void host() {
      if (role != Role.NONE) {
         return;
      }

      nickname = normalizeNickname(nickname, "Host");
      resetState();
      role = Role.HOST;
      status = Status.LISTENING;
      statusText = "Waiting for a player to join...";
      running = true;
      Thread t = new Thread(new Runnable() {
         @Override
         public void run() {
            hostLoop();
         }
      }, "NetHost");
      t.setDaemon(true);
      t.start();
   }

   /** Connects to a host and joins its world once the welcome message arrives. */
   public static synchronized void join(String address) {
      if (role != Role.NONE) {
         return;
      }

      nickname = normalizeNickname(nickname, "Client");
      resetState();
      role = Role.CLIENT;
      status = Status.CONNECTING;
      statusText = "Connecting to " + address + ":" + port + "...";
      running = true;
      final String target = address;
      Thread t = new Thread(new Runnable() {
         @Override
         public void run() {
            clientLoop(target);
         }
      }, "NetClient");
      t.setDaemon(true);
      t.start();
   }

   /** Closes everything and returns to the offline state. */
   public static synchronized void disconnect() {
      if (role == Role.NONE && !running) {
         return;
      }

      // Host: persist the client's progress. Then make sure the writer thread
      // stopped so a final queued push (e.g. Save & Quit) still goes out.
      flushClientState();
      running = false;
      awaitWriter();
      drainOutgoing();
      closeActive();
      closeServer();
      resetState();
   }

   /**
    * Called once per frame from the main loop, in every game state.
    * Handles incoming messages, connection events, state sending and
    * remote player interpolation.
    */
   public static void update(float delta) {
      if (!isActive() && !eventClientJoined && !eventLinkLost) {
         return;
      }

      // Leaving to the menu ends the session (host server included).
      if (isActive() && Main.getGameState() == GameState.LOADING_MENU) {
         disconnect();
         return;
      }

      // Drain queued frames first: the handshake nickname (HELLO) must land
      // before the welcome is built, and the client's final state push before
      // onLinkLost reacts to the closed link.
      byte[] earlyFrame;
      while ((earlyFrame = incoming.poll()) != null) {
         handleMessage(earlyFrame);
      }

      if (eventClientJoined) {
         eventClientJoined = false;
         status = Status.CONNECTED;
         statusText = role == Role.HOST ? remoteNickname + " connected." : "Connected. Receiving world data...";
      }

      // The welcome with the world snapshot must be built on the game thread.
      if (role == Role.HOST && status == Status.CONNECTED && !welcomeSent && handshakeDone && activeOut != null) {
         welcomeSent = true;
         queueRaw(buildWelcome());
      }

      if (eventLinkLost) {
         eventLinkLost = false;
         onLinkLost();
      }

      byte[] frame;
      while ((frame = incoming.poll()) != null) {
         handleMessage(frame);
      }

      // World mutations that arrived while the client was still loading are applied here.
      if (!pendingWorldOps.isEmpty() && isGameplayState()) {
         for (int i = 0; i < pendingWorldOps.size(); i++) {
            applyWorldMessage(pendingWorldOps.get(i));
         }

         pendingWorldOps.clear();
      }

      if (status == Status.CONNECTED && role != Role.NONE && GameScene.avatar != null && isGameplayState()) {
         sendTimer += delta;
         if (sendTimer >= SEND_INTERVAL) {
            sendTimer = 0.0F;
            sendPosition();
            if (role == Role.CLIENT) {
               playerStateTimer += delta;
               if (playerStateTimer >= PLAYER_STATE_INTERVAL) {
                  playerStateTimer = 0.0F;
                  sendPlayerState();
               }
            }
         }

         // Host mirrors slowly changing shared state (water levels, droids).
         if (role == Role.HOST) {
            waterTimer += delta;
            if (waterTimer >= WATER_SEND_INTERVAL) {
               waterTimer = 0.0F;
               sendTimeState();
               sendWaterState();
            }

            droidTimer += delta;
            if (droidTimer >= DROID_SEND_INTERVAL) {
               droidTimer = 0.0F;
               sendDroidSnapshots();
            }

            creatureTimer += delta;
            if (creatureTimer >= CREATURE_SEND_INTERVAL) {
               creatureTimer = 0.0F;
               sendCreatureStates();
            }

            fishTimer += delta;
            if (fishTimer >= FISH_SEND_INTERVAL) {
               fishTimer = 0.0F;
               sendFishStates();
            }
         }
      }

      if (pendingSpawn && Main.getGameState() == GameState.PLAYING && GameScene.avatar != null) {
         pendingSpawn = false;
         if (pendingPieces != null && GameScene.avatar != null) {
            GameScene.avatar.getPlayerState().submarinePieces.clear();
            game.submarine.SubmarinePiece[] allPieces = game.submarine.SubmarinePiece.values();
            for (int i = 0; i < pendingPieces.length; i++) {
               int pieceOrdinal = pendingPieces[i];
               if (pieceOrdinal >= 0 && pieceOrdinal < allPieces.length) {
                  GameScene.avatar.getPlayerState().addSubmarinePiece(allPieces[pieceOrdinal]);
               }
            }

            pendingPieces = null;
         }

         spawnClientAvatar();

         // The host sent our saved progress inside the welcome: restore it now
         // that the fresh avatar exists (position + inventory + safe spot).
         if (pendingPlayerState != null) {
            SaveManager.applyClientState(pendingPlayerState);
            pendingPlayerState = null;
         }
      }

      if (remote != null) {
         remote.update(delta);
      }

   }

   /** Renders the remote player; call from the world render pass with the enemy shader bound. */
   public static void renderRemote() {
      nametagVisible = false;
      if (remote != null && status == Status.CONNECTED) {
         remote.render();
         updateNametag();
      }
   }

   /** The remote peer's avatar, or null while not connected. */
   public static RemotePlayer getRemotePlayer() {
      return status == Status.CONNECTED ? remote : null;
   }

   // ------------------------------------------------------------------
   // Network threads
   // ------------------------------------------------------------------

   private static void hostLoop() {
      try {
         serverSocket = new ServerSocket(port);

         while (running) {
            Socket s = serverSocket.accept();
            if (!running) {
               closeQuietly(s);
               break;
            }

            beginConnection(s);
            startWriter(s, activeOut);
            startReader(s);
            eventClientJoined = true;

            while (running && clientAlive) {
               try {
                  Thread.sleep(50L);
               } catch (InterruptedException e) {
                  break;
               }
            }

            endConnection();
            if (running) {
               eventLinkLost = true;
            }
         }
      } catch (IOException e) {
         if (running) {
            role = Role.NONE;
            status = Status.ERROR;
            statusText = "Cannot listen on port " + port + ": " + e.getMessage();
         }
      }

      endConnection();
      closeServer();
   }

   private static void clientLoop(String address) {
      boolean connected = false;

      try {
         Socket s = new Socket();
         s.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MS);
         beginConnection(s);
         queueRaw(buildHello());
         startWriter(s, activeOut);
         startReader(s);
         connected = true;
         eventClientJoined = true;

         while (running && clientAlive) {
            try {
               Thread.sleep(50L);
            } catch (InterruptedException e) {
               break;
            }
         }
      } catch (Exception e) {
         if (running && !connected) {
            role = Role.NONE;
            status = Status.ERROR;
            statusText = "Connection failed: " + e.getMessage();
         }
      }

      endConnection();
      if (running && connected) {
         eventLinkLost = true;
      }
   }

   private static void beginConnection(Socket s) throws IOException {
      s.setTcpNoDelay(true);
      remoteNickname = "Player";
      activeSocket = s;
      activeOut = new DataOutputStream(s.getOutputStream());
      incoming.clear();
      outgoing.clear();
      pendingWorldOps.clear();
      if (remoteSubIdx >= 0 && GameScene.getSubmarines() != null && remoteSubIdx < GameScene.getSubmarines().size()) {
         GameScene.getSubmarines().get(remoteSubIdx).applyRemoteIdle();
      }

      remoteMoney = 0;
      remoteSubIdx = -1;
      objSyncX = Float.NaN;
      objSyncZ = Float.NaN;
      objSyncBytes = null;
      droidSyncIdx = -1;
      droidSyncBytes = null;
      waterTimer = 0.0F;
      droidTimer = 0.0F;
      creatureTimer = 0.0F;
      knownEnemies.clear();
      fishTimer = 0.0F;
      knownFish.clear();
      clientAlive = true;
      handshakeDone = false;
      pendingPlayerState = null;
      bufferedClientState = null;
      playerStateTimer = 0.0F;
      welcomeSent = false;
      eventClientJoined = false;
      eventLinkLost = false;
   }

   private static void endConnection() {
      clientAlive = false;
      closeActive();
   }

   private static void closeActive() {
      Socket s = activeSocket;
      activeSocket = null;
      activeOut = null;
      closeQuietly(s);
   }

   private static void closeServer() {
      ServerSocket ss = serverSocket;
      serverSocket = null;
      if (ss != null) {
         try {
            ss.close();
         } catch (IOException e) {
            // ignore
         }
      }
   }

   private static void closeQuietly(Socket s) {
      if (s != null) {
         try {
            s.close();
         } catch (IOException e) {
            // ignore
         }
      }
   }

   /** Waits for the writer thread so drainOutgoing can write without races. */
   private static void awaitWriter() {
      Thread wt = writerThread;
      if (wt == null) {
         return;
      }

      try {
         wt.join(100L);
      } catch (InterruptedException e) {
         // ignore
      }
   }

   /** Sends queued frames synchronously; only called after the writer stopped. */
   private static void drainOutgoing() {
      if (writerThread != null && writerThread.isAlive()) {
         return;
      }

      DataOutputStream os = activeOut;
      if (os == null) {
         return;
      }

      byte[] frame;
      while ((frame = outgoing.poll()) != null) {
         try {
            os.writeInt(frame.length);
            os.write(frame);
            os.flush();
         } catch (IOException e) {
            break;
         }
      }
   }

   private static void startReader(final Socket s) {
      Thread t = new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               DataInputStream in = new DataInputStream(s.getInputStream());

               while (running) {
                  int len = in.readInt();
                  if (len <= 0 || len > MAX_FRAME_SIZE) {
                     throw new IOException("Bad frame length " + len);
                  }

                  byte[] frame = new byte[len];
                  in.readFully(frame);
                  incoming.add(frame);
               }
            } catch (IOException e) {
               // Connection closed or broken; the session loop notices via clientAlive.
            } finally {
               clientAlive = false;
            }
         }
      }, "NetReader");
      t.setDaemon(true);
      t.start();
   }

   private static void startWriter(final Socket s, final DataOutputStream os) {
      Thread t = new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               while (running && activeOut == os) {
                  byte[] frame = outgoing.poll();
                  if (frame == null) {
                     try {
                        Thread.sleep(5L);
                     } catch (InterruptedException e) {
                        return;
                     }

                     continue;
                  }

                  os.writeInt(frame.length);
                  os.write(frame);
                  os.flush();
               }
            } catch (IOException e) {
               clientAlive = false;
            }
         }
      }, "NetWriter");
      writerThread = t;
      t.setDaemon(true);
      t.start();
   }

   // ------------------------------------------------------------------
   // Messages
   // ------------------------------------------------------------------

   private static void handleMessage(byte[] frame) {
      if (frame.length < 1) {
         return;
      }

      DataInputStream d = new DataInputStream(new ByteArrayInputStream(frame, 1, frame.length - 1));

      try {
         switch (frame[0]) {
            case MSG_HELLO: {
               handshakeDone = true;
               int clientVersion = d.readInt();
               if (clientVersion != PROTOCOL_VERSION) {
                  statusText = "Client runs an incompatible version.";
                  break;
               }

               remoteNickname = sanitizeNickname(d.readUTF());
               if (role == Role.HOST) {
                  statusText = remoteNickname + " connected.";
               }

               break;
            }
            case MSG_WELCOME:
               if (role == Role.CLIENT) {
                  handleWelcome(d);
               }

               break;
            case MSG_PLAYER_STATE: {
               if (role == Role.HOST) {
                  byte[] progress = new byte[d.available()];
                  d.readFully(progress);
                  if (progress.length > 0) {
                     bufferedClientState = progress;
                  }
               }

               break;
            }
            case MSG_TIME: {
               if (role == Role.CLIENT) {
                  float hostPlayTime = d.readFloat();
                  float hostClock = d.readFloat();
                  float hostLight = d.readFloat();
                  GameTime.sync(hostPlayTime, hostClock, hostLight);
               }

               break;
            }
            case MSG_ENEMY_HIT: {
               // Applied straight away (not through applyWorldMessage) so the
               // resulting loot drop is still broadcast to the client.
               if (role == Role.HOST) {
                  int enemyId = d.readUnsignedShort();
                  float amount = d.readFloat();
                  applyRemoteEnemyHit(enemyId, amount);
               }

               break;
            }
            case MSG_FISH_HIT: {
               if (role == Role.HOST) {
                  int fishId = d.readUnsignedShort();
                  float amount = d.readFloat();
                  applyRemoteFishHit(fishId, amount);
               }

               break;
            }
            case MSG_POS: {
               float x = d.readFloat();
               float y = d.readFloat();
               float z = d.readFloat();
               float yaw = d.readFloat();
               remoteMoney = d.readInt();
               int flags = d.readByte();
               if (remote == null) {
                  remote = new RemotePlayer();
               }

               remote.setTarget(x, y, z, yaw);
               boolean navigating = (flags & 1) != 0;
               if (navigating) {
                  int subIdx = d.readInt();
                  float sx = d.readFloat();
                  float sy = d.readFloat();
                  float sz = d.readFloat();
                  float srx = d.readFloat();
                  float sry = d.readFloat();
                  java.util.ArrayList<game.submarine.Submarine> subs = GameScene.getSubmarines();
                  if (subs != null && subIdx >= 0 && subIdx < subs.size()) {
                     if (remoteSubIdx >= 0 && remoteSubIdx < subs.size() && remoteSubIdx != subIdx) {
                        subs.get(remoteSubIdx).applyRemoteIdle();
                     }

                     subs.get(subIdx).applyRemoteDrive(sx, sy, sz, srx, sry);
                     remoteSubIdx = subIdx;
                  }
               } else if (remoteSubIdx >= 0) {
                  java.util.ArrayList<game.submarine.Submarine> subs = GameScene.getSubmarines();
                  if (subs != null && remoteSubIdx < subs.size()) {
                     subs.get(remoteSubIdx).applyRemoteIdle();
                  }

                  remoteSubIdx = -1;
               }

               remote.setNavigating(navigating);
               break;
            }
            case MSG_CHEST_TAKEN:
            case MSG_PLANT_HARVEST:
            case MSG_ORE_DEPOSIT:
            case MSG_ORE_MINED:
            case MSG_BASE_SPAWN:
            case MSG_BLOCK_OP:
            case MSG_ITEM_SPAWN:
            case MSG_ITEM_TAKE:
            case MSG_CHEST_INVENTORY:
            case MSG_BASE_INVENTORY:
            case MSG_CHAT:
            case MSG_TOMB_SPAWN:
            case MSG_TOMB_INVENTORY:
            case MSG_OBJ_SPAWN:
            case MSG_SUB_SPAWN:
            case MSG_OBJ_INVENTORY:
            case MSG_DROID_SPAWN:
            case MSG_DROID_INVENTORY:
            case MSG_DROID_STATE:
            case MSG_DROID_SYNC:
            case MSG_POT_STATE:
             case MSG_SUB_PIECE:
            case MSG_WATER:
            case MSG_ENEMY_SYNC:
            case MSG_ENEMY_DESPAWN:
            case MSG_FISH_SYNC:
            case MSG_FISH_DESPAWN:
               if (isGameplayState()) {
                  applyWorldMessage(frame);
               } else {
                  // The client world may still be loading; apply once it is playable.
                  pendingWorldOps.add(frame);
               }

               break;
            default:
               break;
         }
      } catch (IOException e) {
         // Malformed frame: drop it, the link level checks will catch real breakage.
      }
   }

   private static void handleWelcome(DataInputStream d) throws IOException {
      if (welcomed) {
         return;
      }

      int version = d.readInt();
      if (version != PROTOCOL_VERSION) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host runs an incompatible version.";
         return;
      }

      int seed = d.readInt();
      int modeOrdinal = d.readByte();
      float dayTime = d.readFloat();
      float nightTime = d.readFloat();
      int spawningOrdinal = d.readByte();
      GameMode[] modes = GameMode.values();
      EnemyGenerator.SpawningLevel[] levels = EnemyGenerator.SpawningLevel.values();
      if (modeOrdinal < 0 || modeOrdinal >= modes.length || spawningOrdinal < 0 || spawningOrdinal >= levels.length) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host sent invalid world data.";
         return;
      }

      int worldLen = d.readInt();
      if (worldLen <= 0 || worldLen > MAX_FRAME_SIZE) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host sent invalid world snapshot.";
         return;
      }

      byte[] worldBytes = new byte[worldLen];
      d.readFully(worldBytes);
      int basesLen = d.readInt();
      if (basesLen <= 0 || basesLen > MAX_FRAME_SIZE) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host sent invalid base snapshot.";
         return;
      }

      byte[] baseBytes = new byte[basesLen];
      d.readFully(baseBytes);
      int objLen = d.readInt();
      if (objLen <= 0 || objLen > MAX_FRAME_SIZE) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host sent invalid object snapshot.";
         return;
      }

      byte[] objBytes = new byte[objLen];
      d.readFully(objBytes);
      int droidLen = d.readInt();
      if (droidLen <= 0 || droidLen > MAX_FRAME_SIZE) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host sent invalid droid snapshot.";
         return;
      }

      byte[] droidBytes = new byte[droidLen];
      d.readFully(droidBytes);
      int subLen = d.readInt();
      if (subLen <= 0 || subLen > MAX_FRAME_SIZE) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host sent invalid submarine snapshot.";
         return;
      }

      byte[] subBytes = new byte[subLen];
      d.readFully(subBytes);
      int tombLen = d.readInt();
      byte[] tombInvBytes = null;
      float tombX = 0.0F;
      float tombY = 0.0F;
      float tombZ = 0.0F;
      if (tombLen > 0) {
         if (tombLen > MAX_FRAME_SIZE) {
            disconnect();
            status = Status.ERROR;
            statusText = "Host sent invalid tomb snapshot.";
            return;
         }

         tombX = d.readFloat();
         tombY = d.readFloat();
         tombZ = d.readFloat();
         tombInvBytes = new byte[tombLen];
         d.readFully(tombInvBytes);
      }

      remoteNickname = sanitizeNickname(d.readUTF());
      int welcomePieceCount = d.readByte();
      pendingPieces = welcomePieceCount > 0 ? new byte[welcomePieceCount] : null;
      for (int i = 0; pendingPieces != null && i < pendingPieces.length; i++) {
         pendingPieces[i] = d.readByte();
      }

      // The joining player's stored progress (inventory + position), if any.
      int progressLen = d.readInt();
      if (progressLen > MAX_FRAME_SIZE) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host sent invalid player data.";
         return;
      }

      pendingPlayerState = progressLen > 0 ? new byte[progressLen] : null;
      if (pendingPlayerState != null) {
         d.readFully(pendingPlayerState);
      }

      World world;
      ArrayList<SeafloorBase> bases;
      java.util.ArrayList<game.outsideObj.OutsideObj> sessionObjects;
      java.util.ArrayList<game.player.droid.Droid> sessionDroids;
      java.util.ArrayList<game.submarine.Submarine> sessionSubs;
      Inventory sessionTomb;
      try {
         world = (World)deserialize(worldBytes);
         bases = (ArrayList<SeafloorBase>)deserialize(baseBytes);
         sessionObjects = (java.util.ArrayList<game.outsideObj.OutsideObj>)deserialize(objBytes);
         sessionDroids = (java.util.ArrayList<game.player.droid.Droid>)deserialize(droidBytes);
         sessionSubs = (java.util.ArrayList<game.submarine.Submarine>)deserialize(subBytes);
         sessionTomb = tombInvBytes == null ? null : (Inventory)deserialize(tombInvBytes);
      } catch (Exception e) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host sent unreadable world data.";
         return;
      }

      if (world == null) {
         disconnect();
         status = Status.ERROR;
         statusText = "Host world is not ready yet.";
         return;
      }

      welcomed = true;
      statusText = "Joining world...";
      GameScene.gameMode = modes[modeOrdinal];
      SaveManager.generateSavePath();
      SeedInput.setSeed(world.getWorldSeed() != 0 ? world.getWorldSeed() : seed);
      WorldManager.dayTime = dayTime;
      WorldManager.nightTime = nightTime;
      WorldManager.spawning = levels[spawningOrdinal];
      Loading.loadGame(world);
      Loading.skipCinematic = true;
      GameScene.registerRemoteBases(bases);
      GameScene.registerRemoteSession(sessionObjects, sessionDroids, sessionSubs);
      if (sessionTomb != null) {
         GameScene.worldChest = new game.player.WorldChest(sessionTomb, new game.util.Point(tombX, tombY, tombZ));
      }
      pendingSpawn = true;
      Main.gameState = GameState.LOADING_GAME;
   }

   private static void sendPosition() {
      Point pos = GameScene.avatar.getPos();
      ByteArrayOutputStream bos = new ByteArrayOutputStream(32);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_POS);
         d.writeFloat(pos.x);
         d.writeFloat(pos.y);
         d.writeFloat(pos.z);
         d.writeFloat(GameScene.avatar.getHorizontalAngle());
         d.writeInt(Main.achievements != null ? Main.achievements.getMoney() : 0);

         byte flags = 0;
         game.submarine.Submarine driveSub = null;
         if (GameScene.avatar.isNavigating()) {
            driveSub = GameScene.getActiveSubmarine();
         }

         int subIdx = driveSub == null || GameScene.getSubmarines() == null ? -1 : GameScene.getSubmarines().indexOf(driveSub);
         if (subIdx >= 0) {
            flags |= 1;
         } else {
            driveSub = null;
         }

         d.writeByte(flags);
         if (driveSub != null) {
            d.writeInt(subIdx);
            d.writeFloat(driveSub.getPosition().x);
            d.writeFloat(driveSub.getPosition().y);
            d.writeFloat(driveSub.getPosition().z);
            d.writeFloat(driveSub.getRotation().x);
            d.writeFloat(driveSub.getRotation().y);
         }
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Role specific default when the nickname is empty or still "Player". */
   private static String normalizeNickname(String raw, String fallback) {
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("Player")) {
         return fallback;
      }

      return trimmed;
   }

   /** Trims, strips control characters and caps the nickname to a sane length. */
   public static String sanitizeNickname(String raw) {
      if (raw == null) {
         return "Player";
      }

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < raw.length() && sb.length() < 16; i++) {
         char c = raw.charAt(i);
         if (c >= ' ') {
            sb.append(c);
         }
      }

      String result = sb.toString().trim();
      return result.isEmpty() ? "Player" : result;
   }

   private static byte[] buildHello() {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(5);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_HELLO);
         d.writeInt(PROTOCOL_VERSION);
         d.writeUTF(sanitizeNickname(nickname));
      } catch (IOException e) {
         // Cannot happen on a byte array stream.
      }

      return bos.toByteArray();
   }

   private static byte[] buildWelcome() {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_WELCOME);
         d.writeInt(PROTOCOL_VERSION);
         d.writeInt(SeedInput.getSeed());
         d.writeByte(GameScene.gameMode.ordinal());
         d.writeFloat(WorldManager.dayTime);
         d.writeFloat(WorldManager.nightTime);
                   // The spawning level lives in the world object: the static is only set
          // for fresh worlds, so a loaded slot must be read through worldManager.
          EnemyGenerator.SpawningLevel spawnLevel = Loading.worldManager != null ? Loading.worldManager.getSpawning() : WorldManager.spawning;
          if (spawnLevel == null) {
             spawnLevel = EnemyGenerator.SpawningLevel.NORMAL;
          }

          d.writeByte(spawnLevel.ordinal());

         // World snapshot: late joiners inherit opened chests, mined ore, harvested plants.
         World hostWorld = Loading.worldManager != null ? Loading.getPendingWorld() : null;
         byte[] worldBytes = serialize(hostWorld);
         d.writeInt(worldBytes.length);
         d.write(worldBytes);

         ArrayList<SeafloorBase> bases = GameScene.getSeafloorBases();
         byte[] baseBytes = serialize(bases == null ? new ArrayList<SeafloorBase>() : new ArrayList<SeafloorBase>(bases));
         d.writeInt(baseBytes.length);
          d.write(baseBytes);

          java.util.ArrayList<game.outsideObj.OutsideObj> objects = GameScene.getOutsideObjects();
          byte[] objBytes = serialize(objects == null ? new java.util.ArrayList<game.outsideObj.OutsideObj>() : new java.util.ArrayList<game.outsideObj.OutsideObj>(objects));
          d.writeInt(objBytes.length);
          d.write(objBytes);

          java.util.ArrayList<game.player.droid.Droid> droidList = GameScene.getDroids();
          byte[] droidBytes = serialize(droidList == null ? new java.util.ArrayList<game.player.droid.Droid>() : new java.util.ArrayList<game.player.droid.Droid>(droidList));
          d.writeInt(droidBytes.length);
          d.write(droidBytes);

          java.util.ArrayList<game.submarine.Submarine> subList = GameScene.getSubmarines();
          byte[] subBytes = serialize(subList == null ? new java.util.ArrayList<game.submarine.Submarine>() : new java.util.ArrayList<game.submarine.Submarine>(subList));
          d.writeInt(subBytes.length);
          d.write(subBytes);

          if (GameScene.worldChest != null && GameScene.worldChest.getInventory() != null) {
             byte[] tombInvBytes = serialize(GameScene.worldChest.getInventory());
             d.writeInt(tombInvBytes.length);
             d.writeFloat(GameScene.worldChest.getPosition().x);
             d.writeFloat(GameScene.worldChest.getPosition().y);
             d.writeFloat(GameScene.worldChest.getPosition().z);
             d.write(tombInvBytes);
          } else {
             d.writeInt(-1);
          }

          d.writeUTF(sanitizeNickname(nickname));

          // Collected submarine pieces are shared progress between both players.
          int hostPieces = 0;
          byte[] hostPieceBytes = new byte[9];
          if (GameScene.avatar != null && GameScene.avatar.getPlayerState() != null) {
             java.util.ArrayList<game.submarine.SubmarinePiece> pieceList = GameScene.avatar.getPlayerState().submarinePieces;
             for (int i = 0; i < pieceList.size() && hostPieces < 9; i++) {
                hostPieceBytes[hostPieces++] = (byte)pieceList.get(i).ordinal();
             }
          }

          d.writeByte(hostPieces);
          d.write(hostPieceBytes, 0, hostPieces);

          // The joining player's own progress, kept in the host's player file.
          byte[] clientProgress = SaveManager.loadPlayerProgress(remoteNickname);
          if (clientProgress != null && clientProgress.length > 0) {
             d.writeInt(clientProgress.length);
             d.write(clientProgress);
          } else {
             d.writeInt(-1);
          }
      } catch (IOException e) {
         // Cannot happen on a byte array stream.
      }

      return bos.toByteArray();
   }

   private static byte[] serialize(Object obj) throws IOException {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(obj);
      oos.flush();
      return bos.toByteArray();
   }

   private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes));
      return ois.readObject();
   }

   private static void queueRaw(byte[] frame) {
      if (activeOut != null) {
         outgoing.add(frame);
      }
   }

   // ------------------------------------------------------------------
   // World state sync (all senders run on the game thread)
   // ------------------------------------------------------------------

   /**
    * True while a session is linked and in game: local world mutations may be
    * broadcast. Suppressed while applying a remote event or during internal,
    * derived mutations (auto walls, loading).
    */
   public static boolean shouldSendWorldEvents() {
      return role != Role.NONE && status == Status.CONNECTED && isGameplayState() && !applyingRemote && suppressSends == 0;
   }

   /** True while the game thread is applying an event received from the other peer. */
   public static boolean isApplyingRemote() {
      return applyingRemote;
   }

   /** Wraps derived mutations (e.g. automatic walls) that must never be broadcast. */
   public static void beginInternalMutation() {
      suppressSends++;
   }

   public static void endInternalMutation() {
      if (suppressSends > 0) {
         suppressSends--;
      }
   }

   /** Reports a treasure chest taken so the other peer empties the same tile. */
   public static void sendChestTaken(int tileX, int tileZ) {
      if (!shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(9);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_CHEST_TAKEN);
         d.writeInt(tileX);
         d.writeInt(tileZ);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Reports a plant harvest segment; the peer replays it without spawning loot. */
   public static void sendPlantHarvest(Segment segment) {
      if (!shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(25);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_PLANT_HARVEST);
         d.writeFloat(segment.start.x);
         d.writeFloat(segment.start.y);
         d.writeFloat(segment.start.z);
         d.writeFloat(segment.end.x);
         d.writeFloat(segment.end.y);
         d.writeFloat(segment.end.z);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Reports a mined ore deposit so the peer lowers the same deposit count. */
   public static void sendOreDepositHarvested(int chunkX, int chunkZ, Point localPos) {
      if (!shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(17);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_ORE_DEPOSIT);
         d.writeInt(chunkX);
         d.writeInt(chunkZ);
         d.writeFloat(localPos.x);
         d.writeFloat(localPos.y);
         d.writeFloat(localPos.z);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Reports mined rock ore so the peer depletes the same world resource counters. */
   public static void sendOreMined(float x, float z, ItemType itemType) {
      if (!shouldSendWorldEvents() || itemType == null) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(10);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_ORE_MINED);
         d.writeFloat(x);
         d.writeFloat(z);
         d.writeByte(itemType.ordinal());
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Reports a newly placed base foundation so the peer spawns the same base. */
   public static void sendBaseSpawn(Point pos) {
      if (!shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(13);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_BASE_SPAWN);
         d.writeFloat(pos.x);
         d.writeFloat(pos.y);
         d.writeFloat(pos.z);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Reports a single block operation (build, element add/remove, material...). */
   public static void sendBlockOp(int baseIdx, int x, int y, int z, int op, int param, int dir) {
      if (!shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(11);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_BLOCK_OP);
         d.writeByte(baseIdx);
         d.writeByte(x);
         d.writeByte(y);
         d.writeByte(z);
         d.writeByte(op);
         d.writeByte(param);
         d.writeByte(dir);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Reports a freshly spawned world item so the peer shows the same pickup. */
   public static void sendItemSpawn(Point pos, ItemType itemType) {
      if (!shouldSendWorldEvents() || itemType == null) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(14);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_ITEM_SPAWN);
         d.writeByte(itemType.ordinal());
         d.writeFloat(pos.x);
         d.writeFloat(pos.y);
         d.writeFloat(pos.z);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Reports an item the local avatar collected so the peer removes it too. */
   public static void sendItemTake(Point pos, ItemType itemType) {
      if (!shouldSendWorldEvents() || itemType == null) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(14);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_ITEM_TAKE);
         d.writeByte(itemType.ordinal());
         d.writeFloat(pos.x);
         d.writeFloat(pos.y);
         d.writeFloat(pos.z);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /**
    * Sends the open world chest's full inventory whenever its content changed;
    * the peer overwrites its own copy of the same chest in place.
    */
   public static void sendChestInventoryIfChanged(int tileX, int tileZ, Inventory inventory) {
      if (tileX < 0 || inventory == null || !shouldSendWorldEvents()) {
         return;
      }

      byte[] bytes;
      try {
         bytes = serialize(inventory);
      } catch (IOException e) {
         return;
      }

      if (chestSyncTileX == tileX && chestSyncTileZ == tileZ && java.util.Arrays.equals(chestSyncBytes, bytes)) {
         return;
      }

      chestSyncTileX = tileX;
      chestSyncTileZ = tileZ;
      chestSyncBytes = bytes;

      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length + 13);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_CHEST_INVENTORY);
         d.writeInt(tileX);
         d.writeInt(tileZ);
         d.writeInt(bytes.length);
         d.write(bytes);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /**
    * Sends the open base element inventory (workshop, cooker, base chest)
    * whenever its content changed; the peer overwrites its copy in place.
    */
   public static void sendBaseInventoryIfChanged(int baseIdx, int bx, int by, int bz, int elemType, Inventory inventory) {
      if (baseIdx < 0 || inventory == null || !shouldSendWorldEvents()) {
         return;
      }

      byte[] bytes;
      try {
         bytes = serialize(inventory);
      } catch (IOException e) {
         return;
      }

      if (baseSyncIdx == baseIdx && baseSyncX == bx && baseSyncY == by && baseSyncZ == bz
         && baseSyncType == elemType && java.util.Arrays.equals(baseSyncBytes, bytes)) {
         return;
      }

      baseSyncIdx = baseIdx;
      baseSyncX = bx;
      baseSyncY = by;
      baseSyncZ = bz;
      baseSyncType = elemType;
      baseSyncBytes = bytes;

      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length + 9);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_BASE_INVENTORY);
         d.writeByte(baseIdx);
         d.writeByte(bx);
         d.writeByte(by);
         d.writeByte(bz);
         d.writeByte(elemType);
         d.writeInt(bytes.length);
         d.write(bytes);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Sends one chat line to the peer. */
   public static void sendChat(String text) {
      if (text == null || text.isEmpty() || status != Status.CONNECTED) {
         return;
      }

      byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length + 5);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_CHAT);
         d.writeInt(bytes.length);
         d.write(bytes);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Announces the local player's death tomb so the peer can spawn the same chest. */
   public static void sendTombSpawn(Inventory inventory, float x, float y, float z) {
      if (inventory == null || !shouldSendWorldEvents()) {
         return;
      }

      byte[] bytes;
      try {
         bytes = serialize(inventory);
      } catch (IOException e) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length + 17);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_TOMB_SPAWN);
         d.writeFloat(x);
         d.writeFloat(y);
         d.writeFloat(z);
         d.writeInt(bytes.length);
         d.write(bytes);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Pushes the open death tomb's contents to the peer whenever they changed. */
   public static void sendTombInventoryIfChanged(Inventory inventory) {
      if (inventory == null || !shouldSendWorldEvents()) {
         return;
      }

      byte[] bytes;
      try {
         bytes = serialize(inventory);
      } catch (IOException e) {
         return;
      }

      if (java.util.Arrays.equals(tombSyncBytes, bytes)) {
         return;
      }

      tombSyncBytes = bytes;
      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length + 5);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_TOMB_INVENTORY);
         d.writeInt(bytes.length);
         d.write(bytes);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Announces a placed outside object (extractor, harpoon cannon, lamp) at its placement point. */
   public static void sendOutsideObjSpawn(int kind, float x, float y, float z) {
      if (!shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(14);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_OBJ_SPAWN);
         d.writeByte(kind);
         d.writeFloat(x);
         d.writeFloat(y);
         d.writeFloat(z);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

      /** Pushes the open outside-object (extractor) inventory to the peer when it changed. */
   public static void sendObjInventoryIfChanged(float x, float z, Inventory inventory) {
      if (Float.isNaN(x) || Float.isNaN(z) || inventory == null || !shouldSendWorldEvents()) {
         return;
      }

      byte[] bytes;
      try {
         bytes = serialize(inventory);
      } catch (IOException e) {
         return;
      }

      if (objSyncX == x && objSyncZ == z && java.util.Arrays.equals(objSyncBytes, bytes)) {
         return;
      }

      objSyncX = x;
      objSyncZ = z;
      objSyncBytes = bytes;

      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length + 9);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_OBJ_INVENTORY);
         d.writeFloat(x);
         d.writeFloat(z);
         d.writeInt(bytes.length);
         d.write(bytes);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Announces a placed droid; the peer adds the same serialized instance. */
   public static void sendDroidSpawn(game.player.droid.Droid droid) {
      if (droid == null || !shouldSendWorldEvents()) {
         return;
      }

      byte[] bytes;
      try {
         bytes = serialize(droid);
      } catch (IOException e) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length + 5);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_DROID_SPAWN);
         d.writeInt(bytes.length);
         d.write(bytes);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Pushes the open droid inventory to the peer when it changed. */
   public static void sendDroidInventoryIfChanged(int droidIdx, Inventory inventory) {
      if (droidIdx < 0 || inventory == null || !shouldSendWorldEvents()) {
         return;
      }

      byte[] bytes;
      try {
         bytes = serialize(inventory);
      } catch (IOException e) {
         return;
      }

      if (droidSyncIdx == droidIdx && java.util.Arrays.equals(droidSyncBytes, bytes)) {
         return;
      }

      droidSyncIdx = droidIdx;
      droidSyncBytes = bytes;

      ByteArrayOutputStream bos = new ByteArrayOutputStream(bytes.length + 6);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_DROID_INVENTORY);
         d.writeByte(droidIdx);
         d.writeInt(bytes.length);
         d.write(bytes);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Human command: droid mode change (stateOrdinal = DroidState ordinal, -1 = repaired). */
   public static void sendDroidState(int droidIdx, int stateOrdinal) {
      if (droidIdx < 0 || !shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(3);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_DROID_STATE);
         d.writeByte(droidIdx);
         d.writeByte(stateOrdinal);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Human command: the local player repaired the droid (their spheres were spent). */
   public static void sendDroidFixed(int droidIdx) {
      sendDroidState(droidIdx, -1);
   }

   /** Announces a placed submarine; the peer spawns the same boat. */
   public static void sendSubmarineSpawn(float x, float y, float z) {
      if (!shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(13);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_SUB_SPAWN);
         d.writeFloat(x);
         d.writeFloat(y);
         d.writeFloat(z);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /**
    * Human action: plant (cropIdx 0..2) or harvest (cropIdx -1) a shared base
    * plant pot; growth then continues deterministically on both sides.
    */
   public static void sendPotState(game.seafloorBase.Octree octree, int bx, int by, int bz, int cropIdx) {
      if (cropIdx < -1 || cropIdx > 2 || !shouldSendWorldEvents()) {
         return;
      }

      ArrayList<SeafloorBase> bases = GameScene.getSeafloorBases();
      if (bases == null) {
         return;
      }

      int baseIdx = -1;
      for (int i = 0; i < bases.size(); i++) {
         if (bases.get(i).getOctree() == octree) {
            baseIdx = i;
            break;
         }
      }

      if (baseIdx < 0) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(6);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_POT_STATE);
         d.writeByte(baseIdx);
         d.writeByte(bx);
         d.writeByte(by);
         d.writeByte(bz);
         d.writeByte(cropIdx);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Human action: the local player picked up a submarine piece off the seafloor. */
   public static void sendSubmarinePieceTaken(int tileX, int tileZ, game.submarine.SubmarinePiece piece, game.util.Point pos, boolean completed) {
      if (piece == null || !shouldSendWorldEvents()) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(20);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_SUB_PIECE);
         d.writeInt(tileX);
         d.writeInt(tileZ);
         d.writeByte(piece.ordinal());
         d.writeFloat(pos.x);
         d.writeFloat(pos.y);
         d.writeFloat(pos.z);
         d.writeBoolean(completed);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Host: pushes every base's water level fill so floods match on both sides. */
   private static void sendWaterState() {
      ArrayList<SeafloorBase> bases = GameScene.getSeafloorBases();
      if (bases == null) {
         return;
      }

      for (int i = 0; i < bases.size(); i++) {
         if (bases.get(i).getOctree() == null) {
            continue;
         }

         byte[] data = bases.get(i).getOctree().serializeWaterParams();
         ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length + 6);
         DataOutputStream d = new DataOutputStream(bos);

         try {
            d.writeByte(MSG_WATER);
            d.writeByte(i);
            d.writeInt(data.length);
            d.write(data);
         } catch (IOException e) {
            return;
         }

         queueRaw(bos.toByteArray());
      }
   }

   /** Host: pushes the authoritative day/night clock so clients stay in sync. */
   private static void sendTimeState() {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(13);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_TIME);
         d.writeFloat(GameTime.totalPlayTime);
         d.writeFloat(GameTime.dayTime);
         d.writeFloat(GameTime.getLightLevel());
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Host: mirrors droid positions and modes to the client several times per second. */
   private static void sendDroidSnapshots() {
      java.util.ArrayList<game.player.droid.Droid> list = GameScene.getDroids();
      if (list == null) {
         return;
      }

      for (int i = 0; i < list.size(); i++) {
         game.player.droid.Droid droid = list.get(i);
         game.util.Point p = droid.getPosition();
         ByteArrayOutputStream bos = new ByteArrayOutputStream(18);
         DataOutputStream d = new DataOutputStream(bos);

         try {
            d.writeByte(MSG_DROID_SYNC);
            d.writeByte(i);
            d.writeFloat(p.x);
            d.writeFloat(p.y);
            d.writeFloat(p.z);
            d.writeByte(droid.getState().ordinal());
            d.writeByte(droid.isWorking() ? 1 : 0);
         } catch (IOException e) {
            return;
         }

         queueRaw(bos.toByteArray());
      }
   }

   /** Host: broadcasts every creature's authoritative state, 10 times per second. */
   private static void sendCreatureStates() {
      if (GameScene.enemyManager == null) {
         return;
      }

      java.util.ArrayList<game.enemy.Enemy> list = GameScene.enemyManager.getEnemies();
      java.util.HashSet<Integer> present = new java.util.HashSet<Integer>();

      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).getNetId() >= 0) {
            present.add(list.get(i).getNetId());
         }
      }

      // Anything we announced that is gone now has to be dropped on the client.
      java.util.Iterator<Integer> knownIt = knownEnemies.iterator();
      while (knownIt.hasNext()) {
         int id = knownIt.next();
         if (!present.contains(id)) {
            knownIt.remove();
            sendEnemyDespawn(id);
         }
      }

      for (int start = 0; start < list.size(); start += CREATURE_BATCH) {
         int end = Math.min(list.size(), start + CREATURE_BATCH);
         int count = 0;

         for (int i = start; i < end; i++) {
            if (list.get(i).getNetId() >= 0) {
               count++;
            }
         }

         if (count == 0) {
            continue;
         }

         ByteArrayOutputStream bos = new ByteArrayOutputStream(3 + count * 34);
         DataOutputStream d = new DataOutputStream(bos);

         try {
            d.writeByte(MSG_ENEMY_SYNC);
            d.writeShort(count);

            for (int i = start; i < end; i++) {
               game.enemy.Enemy enemy = list.get(i);
               if (enemy.getNetId() < 0) {
                  continue;
               }

               writeCreature(d, enemy);
               knownEnemies.add(enemy.getNetId());
            }
         } catch (IOException e) {
            return;
         }

         queueRaw(bos.toByteArray());
      }
   }

   /** One creature entry of MSG_ENEMY_SYNC: id, type, transform, health, animation. */
   private static void writeCreature(DataOutputStream d, game.enemy.Enemy enemy) throws IOException {
      Point p = enemy.getPosition();
      if (p == null) {
         p = new Point();
      }

      Point dir = enemy.getNetFacingForSync();
      float dx = dir.x;
      float dy = dir.y;
      float dz = dir.z;
      if (dx * dx + dy * dy + dz * dz < 1.0E-4F) {
         dx = 0.0F;
         dy = 0.0F;
         dz = 1.0F;
      }

      int flags = enemy.getNetStateForSync() & 7;
      if (enemy.isNetDying()) {
         flags |= 8;
      }

      if (enemy.isNetDead()) {
         flags |= 16;
      }

      d.writeShort(enemy.getNetId());
      d.writeByte(enemy.getType().ordinal());
      d.writeFloat(p.x);
      d.writeFloat(p.y);
      d.writeFloat(p.z);
      d.writeFloat(dx);
      d.writeFloat(dy);
      d.writeFloat(dz);
      d.writeFloat(enemy.getNetHealthForSync());
      d.writeByte(flags);
      d.writeByte(toByte(enemy.getNetMouthForSync()));
      d.writeByte(toByte(enemy.getNetHitFlashForSync()));
   }

   private static int toByte(float unit) {
      int value = (int)(unit * 255.0F);
      if (value < 0) {
         return 0;
      }

      return value > 255 ? 255 : value;
   }

   /** Host: tells the client that a creature was removed from the world. */
   public static void sendEnemyDespawn(int id) {
      if (id < 0 || role != Role.HOST || status != Status.CONNECTED || applyingRemote) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(3);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_ENEMY_DESPAWN);
         d.writeShort(id);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Client: reports damage it applied to a mirrored creature so the host can resolve it. */
   public static void sendEnemyHit(int id, float amount) {
      if (id < 0 || amount <= 0.0F || role != Role.CLIENT || status != Status.CONNECTED || applyingRemote) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(7);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_ENEMY_HIT);
         d.writeShort(id);
         d.writeFloat(amount);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Host: applies a hit the client reported against one of our creatures. */
   private static void applyRemoteEnemyHit(int id, float amount) {
      if (GameScene.enemyManager == null || amount <= 0.0F) {
         return;
      }

      game.enemy.Enemy enemy = GameScene.enemyManager.getByNetId(id);
      if (enemy != null && !enemy.isRemoteControlled()) {
         enemy.applyRemoteDamage(amount);
      }
   }

   /** Host: allocates the next stable fish id for a freshly spawned fish. */
   public static int nextFishId() {
      return nextFishId++;
   }

   /** Distance from a point to whichever player (local or peer) is closer. */
   private static float distanceToNearestAnchor(Point point) {
      if (point == null) {
         return 0.0F;
      }

      float distance = point.distanceTo(game.manager.Camera.getPosition());
      game.net.RemotePlayer remotePlayer = getRemotePlayer();
      if (remotePlayer != null) {
         distance = Math.min(distance, point.distanceTo(remotePlayer.getRenderPos()));
      }

      return distance;
   }

   /**
    * Host: broadcasts the fish around both players a few times per second.
    * Only fish close to a player are packed (interest management); the client
    * creates the ones it has not seen yet and drops the ones we stop naming.
    */
   private static void sendFishStates() {
      game.environment.life.SeaLifeManager seaLife = game.environment.EnvironmentManager.getSeaLifeManager();
      if (seaLife == null) {
         return;
      }

      float limit = ChunkManager.viewDistance * 1.2F;
      fishSyncScratch.clear();
      seaLife.collectAll(fishSyncScratch);

      fishEligible.clear();
      java.util.HashSet<Integer> present = new java.util.HashSet<Integer>();

      for (int i = 0; i < fishSyncScratch.size(); i++) {
         game.environment.life.Fish fish = fishSyncScratch.get(i);
         if (fish.getNetId() < 0 || distanceToNearestAnchor(fish.getPos()) > limit) {
            continue;
         }

         fishEligible.add(fish);
         present.add(fish.getNetId());
      }

      // Anything we announced that is gone now has to be dropped on the client.
      java.util.Iterator<Integer> knownIt = knownFish.iterator();
      while (knownIt.hasNext()) {
         int id = knownIt.next();
         if (!present.contains(id)) {
            knownIt.remove();
            sendFishDespawn(id);
         }
      }

      for (int start = 0; start < fishEligible.size(); start += FISH_BATCH) {
         int end = Math.min(fishEligible.size(), start + FISH_BATCH);
         ByteArrayOutputStream bos = new ByteArrayOutputStream(3 + (end - start) * 20);
         DataOutputStream d = new DataOutputStream(bos);

         try {
            d.writeByte(MSG_FISH_SYNC);
            d.writeShort(end - start);

            for (int i = start; i < end; i++) {
               game.environment.life.Fish fish = fishEligible.get(i);
               game.util.Point pos = fish.getPos();
               game.util.Point rot = fish.getRotation();
               int typeOrdinal = fish.getFishType() == null ? 0 : fish.getFishType().ordinal();
               int packed = (game.environment.life.SeaLifeManager.kindOf(fish) & 7) | (typeOrdinal << 3);

               d.writeShort(fish.getNetId());
               d.writeByte(packed);
               d.writeFloat(pos.x);
               d.writeFloat(pos.y);
               d.writeFloat(pos.z);
               d.writeShort(toAngleShort(rot.x));
               d.writeShort(toAngleShort(rot.y));
               knownFish.add(fish.getNetId());
            }
         } catch (IOException e) {
            return;
         }

         queueRaw(bos.toByteArray());
      }
   }

   /** Angle in degrees packed into a short with half degree steps. */
   private static int toAngleShort(float degrees) {
      int value = (int)Math.round(degrees * 2.0);
      if (value < Short.MIN_VALUE) {
         return Short.MIN_VALUE;
      }

      return value > Short.MAX_VALUE ? Short.MAX_VALUE : value;
   }

   /** Host: tells the client that a fish was removed from the world. */
   public static void sendFishDespawn(int id) {
      if (id < 0 || role != Role.HOST || status != Status.CONNECTED || applyingRemote) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(3);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_FISH_DESPAWN);
         d.writeShort(id);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Client: reports damage it applied to a mirrored fish so the host can resolve it. */
   public static void sendFishHit(int id, float amount) {
      if (id < 0 || amount <= 0.0F || role != Role.CLIENT || status != Status.CONNECTED || applyingRemote) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(7);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_FISH_HIT);
         d.writeShort(id);
         d.writeFloat(amount);
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   /** Host: applies a hit the client reported against one of our fish. */
   private static void applyRemoteFishHit(int id, float amount) {
      game.environment.life.SeaLifeManager seaLife = game.environment.EnvironmentManager.getSeaLifeManager();
      if (seaLife == null || amount <= 0.0F) {
         return;
      }

      game.environment.life.Fish fish = seaLife.getByNetId(id);
      if (fish != null && !fish.isRemoteControlled()) {
         fish.applyRemoteDamage(amount);
      }
   }

/** Applies a world state event received from the other peer (game thread). */
   private static void applyWorldMessage(byte[] frame) {
      if (frame.length < 1) {
         return;
      }

      DataInputStream d = new DataInputStream(new ByteArrayInputStream(frame, 1, frame.length - 1));
      applyingRemote = true;

      try {
         switch (frame[0]) {
            case MSG_CHEST_TAKEN: {
               int tileX = d.readInt();
               int tileZ = d.readInt();
               if (Loading.worldManager != null) {
                  Loading.worldManager.setGamePlayElmtAt(new game.world.structure.GamePlayElmt(game.world.structure.GamePlayType.NONE), tileX, tileZ);
               }

               game.chunks.Chunk chunk = ChunkManager.getActiveChunkAt(tileX * 128, tileZ * 128);
               if (chunk != null) {
                  chunk.removeTreasureChest(tileX, tileZ);
               }

               break;
            }
            case MSG_PLANT_HARVEST: {
               float x1 = d.readFloat();
               float y1 = d.readFloat();
               float z1 = d.readFloat();
               float x2 = d.readFloat();
               float y2 = d.readFloat();
               float z2 = d.readFloat();
               ChunkManager.applyRemotePlantHarvest(new Segment(new Point(x1, y1, z1), new Point(x2, y2, z2)));
               break;
            }
            case MSG_ORE_DEPOSIT: {
               int chunkX = d.readInt();
               int chunkZ = d.readInt();
               Point localPos = new Point(d.readFloat(), d.readFloat(), d.readFloat());
               game.chunks.Chunk chunk = ChunkManager.getActiveChunkAt(chunkX * 128, chunkZ * 128);
               if (chunk != null) {
                  chunk.applyRemoteOreDeposit(localPos);
               }

               break;
            }
            case MSG_ORE_MINED: {
               float x = d.readFloat();
               float z = d.readFloat();
               int typeOrdinal = d.readByte();
               ItemType[] types = ItemType.values();
               if (typeOrdinal >= 0 && typeOrdinal < types.length) {
                  ChunkManager.onOreMined(x, z, types[typeOrdinal]);
               }

               break;
            }
            case MSG_BASE_SPAWN: {
               Point pos = new Point(d.readFloat(), d.readFloat(), d.readFloat());
               GameScene.spawnRemoteSeafloorBase(pos);
               break;
            }
            case MSG_ITEM_SPAWN: {
               int typeOrdinal = d.readByte();
               float x = d.readFloat();
               float y = d.readFloat();
               float z = d.readFloat();
               ItemType[] types = ItemType.values();
               if (typeOrdinal >= 0 && typeOrdinal < types.length) {
                  game.environment.EnvironmentManager.applyRemoteItemSpawn(new Point(x, y, z), types[typeOrdinal]);
               }

               break;
            }
            case MSG_ITEM_TAKE: {
               int typeOrdinal = d.readByte();
               float x = d.readFloat();
               float y = d.readFloat();
               float z = d.readFloat();
               ItemType[] types = ItemType.values();
               if (typeOrdinal >= 0 && typeOrdinal < types.length) {
                  game.environment.EnvironmentManager.applyRemoteItemTake(new Point(x, y, z), types[typeOrdinal]);
               }

               break;
            }
            case MSG_CHEST_INVENTORY: {
               int tileX = d.readInt();
               int tileZ = d.readInt();
               int len = d.readInt();
               if (len <= 0 || len > MAX_FRAME_SIZE) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               Inventory inv;
               try {
                  inv = (Inventory)deserialize(bytes);
               } catch (Exception e) {
                  break;
               }

               // Remember the snapshot so this side does not echo it straight back.
               chestSyncTileX = tileX;
               chestSyncTileZ = tileZ;
               chestSyncBytes = bytes;
               applyChestInventory(tileX, tileZ, inv);
               break;
            }
            case MSG_BASE_INVENTORY: {
               int baseIdx = d.readByte();
               int bx = d.readByte();
               int by = d.readByte();
               int bz = d.readByte();
               int typeOrdinal = d.readByte();
               int len = d.readInt();
               if (len <= 0 || len > MAX_FRAME_SIZE) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               Inventory inv;
               try {
                  inv = (Inventory)deserialize(bytes);
               } catch (Exception e) {
                  break;
               }

               baseSyncIdx = baseIdx;
               baseSyncX = bx;
               baseSyncY = by;
               baseSyncZ = bz;
               baseSyncType = typeOrdinal;
               baseSyncBytes = bytes;
               ArrayList<SeafloorBase> bases = GameScene.getSeafloorBases();
               if (bases != null && baseIdx >= 0 && baseIdx < bases.size()) {
                  game.seafloorBase.util.BlockType[] types = game.seafloorBase.util.BlockType.values();
                  if (typeOrdinal >= 0 && typeOrdinal < types.length) {
                     bases.get(baseIdx).getOctree().applyRemoteElementInventory(bx, by, bz, types[typeOrdinal], inv);
                  }
               }

               break;
            }
            case MSG_CHAT: {
               int len = d.readInt();
               if (len <= 0 || len > 8192) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               game.gui.ChatHud.showMessage(remoteNickname + ": " + new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
               break;
            }
            case MSG_TOMB_SPAWN: {
               float px = d.readFloat();
               float py = d.readFloat();
               float pz = d.readFloat();
               int len = d.readInt();
               if (len <= 0 || len > MAX_FRAME_SIZE) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               Inventory inv;
               try {
                  inv = (Inventory)deserialize(bytes);
               } catch (Exception e) {
                  break;
               }

               GameScene.worldChest = new game.player.WorldChest(inv, new game.util.Point(px, py, pz));
               break;
            }
            case MSG_TOMB_INVENTORY: {
               int len = d.readInt();
               if (len <= 0 || len > MAX_FRAME_SIZE) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               Inventory inv;
               try {
                  inv = (Inventory)deserialize(bytes);
               } catch (Exception e) {
                  break;
               }

               tombSyncBytes = bytes;
               if (GameScene.worldChest != null && GameScene.worldChest.getInventory() != null) {
                  copyInventoryContents(inv, GameScene.worldChest.getInventory());
               }

               break;
            }
            case MSG_OBJ_SPAWN: {
               int kind = d.readByte();
               float x = d.readFloat();
               float y = d.readFloat();
               float z = d.readFloat();
               GameScene.applyRemoteOutsideObject(kind, x, y, z);
               break;
            }
            case MSG_OBJ_INVENTORY: {
               float ox = d.readFloat();
               float oz = d.readFloat();
               int len = d.readInt();
               if (len <= 0 || len > MAX_FRAME_SIZE) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               Inventory inv;
               try {
                  inv = (Inventory)deserialize(bytes);
               } catch (Exception e) {
                  break;
               }

               objSyncX = ox;
               objSyncZ = oz;
               objSyncBytes = bytes;
               java.util.ArrayList<game.outsideObj.OutsideObj> objs = GameScene.getOutsideObjects();
               if (objs != null) {
                  for (int i = 0; i < objs.size(); i++) {
                     game.util.Point p = objs.get(i).getPosition();
                     if (Math.abs(p.x - ox) < 0.05F && Math.abs(p.z - oz) < 0.05F) {
                        if (objs.get(i) instanceof game.outsideObj.Extractor) {
                           copyInventoryContents(inv, ((game.outsideObj.Extractor)objs.get(i)).getInventory());
                        }

                        break;
                     }
                  }
               }

               break;
            }
            case MSG_DROID_SPAWN: {
               int len = d.readInt();
               if (len <= 0 || len > MAX_FRAME_SIZE) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               try {
                  GameScene.spawnDroid((game.player.droid.Droid)deserialize(bytes), false);
               } catch (Exception e) {
                  // Unreadable droid payload: ignore.
               }

               break;
            }
            case MSG_DROID_INVENTORY: {
               int droidIdx = d.readByte();
               int len = d.readInt();
               if (len <= 0 || len > MAX_FRAME_SIZE) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               Inventory inv;
               try {
                  inv = (Inventory)deserialize(bytes);
               } catch (Exception e) {
                  break;
               }

               droidSyncIdx = droidIdx;
               droidSyncBytes = bytes;
               java.util.ArrayList<game.player.droid.Droid> droidList = GameScene.getDroids();
               if (droidList != null && droidIdx >= 0 && droidIdx < droidList.size()) {
                  copyInventoryContents(inv, droidList.get(droidIdx).getInventory());
               }

               break;
            }
            case MSG_DROID_STATE: {
               int droidIdx = d.readByte();
               int cmd = d.readByte();
               java.util.ArrayList<game.player.droid.Droid> droidList = GameScene.getDroids();
               if (droidList != null && droidIdx >= 0 && droidIdx < droidList.size()) {
                  game.player.droid.Droid droid = droidList.get(droidIdx);
                  if (cmd == -1) {
                     droid.applyRemoteFixed();
                  } else {
                     game.player.droid.DroidState[] states = game.player.droid.DroidState.values();
                     if (cmd >= 0 && cmd < states.length) {
                        droid.applyRemoteDroidState(states[cmd]);
                     }
                  }
               }

               break;
            }
            case MSG_DROID_SYNC: {
               int droidIdx = d.readByte();
               float dx = d.readFloat();
               float dy = d.readFloat();
               float dz = d.readFloat();
               int stateOrdinal = d.readByte();
               boolean isWorking = d.readByte() != 0;
               java.util.ArrayList<game.player.droid.Droid> droidList = GameScene.getDroids();
               if (droidList != null && droidIdx >= 0 && droidIdx < droidList.size()) {
                  droidList.get(droidIdx).applyRemoteState(dx, dy, dz, stateOrdinal, isWorking);
               }

               break;
            }
            case MSG_ENEMY_SYNC: {
               int count = d.readUnsignedShort();
               game.enemy.EnemyManager manager = GameScene.enemyManager;
               game.enemy.EnemyType[] enemyTypes = game.enemy.EnemyType.values();

               for (int i = 0; i < count; i++) {
                  int enemyId = d.readUnsignedShort();
                  int typeOrd = d.readUnsignedByte();
                  float ex = d.readFloat();
                  float ey = d.readFloat();
                  float ez = d.readFloat();
                  float dirX = d.readFloat();
                  float dirY = d.readFloat();
                  float dirZ = d.readFloat();
                  float eHealth = d.readFloat();
                  int flags = d.readUnsignedByte();
                  int mouth = d.readUnsignedByte();
                  int flash = d.readUnsignedByte();

                  if (manager == null || typeOrd >= enemyTypes.length) {
                     continue;
                  }

                  game.enemy.Enemy enemy = manager.getByNetId(enemyId);
                  if (enemy == null) {
                     enemy = manager.spawnRemote(enemyId, new Point(ex, ey, ez), enemyTypes[typeOrd]);
                  }

                  if (enemy != null) {
                     enemy.applyNetState(ex, ey, ez, dirX, dirY, dirZ, eHealth,
                           flags & 7, mouth / 255.0F, flash / 255.0F, (flags & 8) != 0, (flags & 16) != 0);
                  }
               }

               break;
            }
            case MSG_ENEMY_DESPAWN: {
               int enemyId = d.readUnsignedShort();
               if (GameScene.enemyManager != null) {
                  GameScene.enemyManager.removeRemote(enemyId);
               }

               break;
            }
            case MSG_FISH_SYNC: {
               int count = d.readUnsignedShort();
               game.environment.life.SeaLifeManager seaLife = game.environment.EnvironmentManager.getSeaLifeManager();
               game.environment.life.FishType[] fishTypes = game.environment.life.FishType.values();

               for (int i = 0; i < count; i++) {
                  int fishId = d.readUnsignedShort();
                  int packed = d.readUnsignedByte();
                  float fx = d.readFloat();
                  float fy = d.readFloat();
                  float fz = d.readFloat();
                  float rotX = d.readShort() / 2.0F;
                  float rotY = d.readShort() / 2.0F;

                  if (seaLife == null) {
                     continue;
                  }

                  game.environment.life.Fish fish = seaLife.getByNetId(fishId);
                  if (fish == null) {
                     int typeOrdinal = (packed >> 3) & 31;
                     if (typeOrdinal >= fishTypes.length) {
                        typeOrdinal = 0;
                     }

                     fish = seaLife.spawnRemote(fishId, packed & 7, fishTypes[typeOrdinal],
                           new Point(fx, fy, fz), rotX, rotY);
                  }

                  if (fish != null) {
                     fish.applyNetState(fx, fy, fz, rotX, rotY);
                  }
               }

               break;
            }
            case MSG_FISH_DESPAWN: {
               int fishId = d.readUnsignedShort();
               game.environment.life.SeaLifeManager seaLife = game.environment.EnvironmentManager.getSeaLifeManager();
               if (seaLife != null) {
                  seaLife.removeRemote(fishId);
               }

               break;
            }
            case MSG_WATER: {
               int baseIdx = d.readByte();
               int len = d.readInt();
               if (len <= 0 || len > MAX_FRAME_SIZE) {
                  break;
               }

               byte[] bytes = new byte[len];
               d.readFully(bytes);
               ArrayList<SeafloorBase> waterBases = GameScene.getSeafloorBases();
               if (waterBases != null && baseIdx >= 0 && baseIdx < waterBases.size() && waterBases.get(baseIdx).getOctree() != null) {
                  waterBases.get(baseIdx).getOctree().applyWaterParams(bytes);
               }

               break;
            }
            case MSG_SUB_SPAWN: {
               float sx = d.readFloat();
               float sy = d.readFloat();
               float sz = d.readFloat();
               GameScene.spawnSubmarine(new Point(sx, sy, sz), false);
               break;
            }
            case MSG_SUB_PIECE: {
               int pieceTileX = d.readInt();
               int pieceTileZ = d.readInt();
               int pieceOrdinal = d.readByte();
               float pieceX = d.readFloat();
               float pieceY = d.readFloat();
               float pieceZ = d.readFloat();
               boolean pieceCompleted = d.readBoolean();
               if (Loading.worldManager != null) {
                  Loading.worldManager.setGamePlayElmtAt(new game.world.structure.GamePlayElmt(game.world.structure.GamePlayType.NONE), pieceTileX, pieceTileZ);
               }

               game.chunks.Chunk pieceChunk = ChunkManager.getActiveChunkAt(pieceTileX * 128, pieceTileZ * 128);
               if (pieceChunk != null) {
                  pieceChunk.removeSubmarinePart(pieceTileX, pieceTileZ);
               }

               game.submarine.SubmarinePiece[] allPieces = game.submarine.SubmarinePiece.values();
               if (pieceOrdinal >= 0 && pieceOrdinal < allPieces.length && GameScene.avatar != null && !GameScene.avatar.hasSubmarinePiece(allPieces[pieceOrdinal])) {
                  int piecesBefore = GameScene.avatar.getSubmarinePiecesCount();
                  GameScene.avatar.getPlayerState().addSubmarinePiece(allPieces[pieceOrdinal]);
                  game.submarine.SubmarineHud.onPieceFound(allPieces[pieceOrdinal]);
                  if (pieceCompleted && piecesBefore + 1 == 9) {
                     GameScene.spawnSubmarine(new game.util.Point(pieceX, pieceY, pieceZ), false);
                  }
               }

               break;
            }
            case MSG_POT_STATE: {
               int baseIdx = d.readByte();
               int bx = d.readByte();
               int by = d.readByte();
               int bz = d.readByte();
               int cropIdx = d.readByte();
               ArrayList<SeafloorBase> potBases = GameScene.getSeafloorBases();
               if (potBases != null && baseIdx >= 0 && baseIdx < potBases.size() && potBases.get(baseIdx).getOctree() != null) {
                  potBases.get(baseIdx).getOctree().applyPotState(bx, by, bz, cropIdx);
               }

               break;
            }
            case MSG_BLOCK_OP: {
               int baseIdx = d.readByte();
               int bx = d.readByte();
               int by = d.readByte();
               int bz = d.readByte();
               int op = d.readByte();
               int p1 = d.readByte();
               int p2 = d.readByte();
               ArrayList<SeafloorBase> bases = GameScene.getSeafloorBases();
               if (bases != null && baseIdx >= 0 && baseIdx < bases.size()) {
                  bases.get(baseIdx).getOctree().applyRemoteBlockOp(bx, by, bz, op, p1, p2);
               }

               break;
            }
         }
      } catch (IOException e) {
         // Malformed frame: drop it, the link level checks will catch real breakage.
      } finally {
         applyingRemote = false;
      }
   }

   /**
    * Overwrites the peer's copy of a chest inventory in place. The chest element
    * and the world record share this object, so the GUI, the lid state and the
    * map icon all update from the same mutation.
    */
   private static void applyChestInventory(int tileX, int tileZ, Inventory source) {
      if (Loading.worldManager == null || source == null) {
         return;
      }

      game.world.structure.GamePlayElmt elmt = Loading.worldManager.getGamePlayElmtAt(tileX, tileZ);
      if (elmt == null || elmt.getType() != game.world.structure.GamePlayType.CHEST) {
         return;
      }

      Inventory target = elmt.getInventory();
      if (target == null) {
         elmt.setInventory(source);
         return;
      }

      copyInventoryContents(source, target);
   }

   /** Replaces the target inventory's contents (and internal state) with the source's copy. */
   public static void copyInventoryContents(Inventory source, Inventory target) {
      if (source == null || target == null) {
         return;
      }

      target.copyStateFrom(source);
   }

   // ------------------------------------------------------------------
   // Client progress (stored by the host) and remote nametag
   // ------------------------------------------------------------------

   /**
    * Client: queues the current progress (position + inventory) for the host,
    * which stores it in save/players. A no-op on the host side.
    */
   public static void sendPlayerState() {
      if (role != Role.CLIENT || status != Status.CONNECTED || !welcomed || GameScene.avatar == null) {
         return;
      }

      byte[] progress = SaveManager.createClientState();
      if (progress == null || progress.length == 0 || progress.length > MAX_FRAME_SIZE) {
         return;
      }

      ByteArrayOutputStream bos = new ByteArrayOutputStream(progress.length + 1);
      DataOutputStream d = new DataOutputStream(bos);
      try {
         d.writeByte(MSG_PLAYER_STATE);
         d.write(progress);
         queueRaw(bos.toByteArray());
      } catch (IOException e) {
         // Cannot happen on a byte array stream.
      }
   }

   /** Host: writes the progress received from the client to its player file. */
   public static void flushClientState() {
      if (role != Role.HOST || bufferedClientState == null) {
         return;
      }

      SaveManager.savePlayerProgress(remoteNickname, bufferedClientState);
   }

   /** True while the remote player's nickname tag was projected on screen. */
   public static boolean isNametagVisible() {
      return nametagVisible;
   }

   public static float getNametagX() {
      return nametagX;
   }

   public static float getNametagY() {
      return nametagY;
   }

   /**
    * Projects the remote player's head to screen space while the world matrices
    * are still bound (called from renderRemote), so PlayerHud can draw their
    * nickname above the sprite. Hidden while they drive a submarine or are far.
    */
   private static void updateNametag() {
      if (remote.isNavigating() || GameScene.avatar == null) {
         return;
      }

      Point renderPos = remote.getRenderPos();
      Point ownPos = GameScene.avatar.getPos();
      float dx = renderPos.x - ownPos.x;
      float dy = renderPos.y - ownPos.y;
      float dz = renderPos.z - ownPos.z;
      if (dx * dx + dy * dy + dz * dz > 100.0F * 100.0F) {
         return;
      }

      nametagVisible = projectToScreen(renderPos.x, renderPos.y + 26.0F, renderPos.z);
   }

   /** Manual clip-space transform of one world point (top-down screen coords). */
   private static boolean projectToScreen(float wx, float wy, float wz) {
      try {
      projModel.rewind();
      GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, projModel);
      projProj.rewind();
      GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projProj);
      projViewport.rewind();
      GL11.glGetFloat(GL11.GL_VIEWPORT, projViewport);

      float[] m = new float[16];
      float[] p = new float[16];
      projModel.get(0, m);
      projProj.get(0, p);

      float x = m[0] * wx + m[4] * wy + m[8] * wz + m[12];
      float y = m[1] * wx + m[5] * wy + m[9] * wz + m[13];
      float z = m[2] * wx + m[6] * wy + m[10] * wz + m[14];
      float w = m[3] * wx + m[7] * wy + m[11] * wz + m[15];

      float cx = p[0] * x + p[4] * y + p[8] * z + p[12] * w;
      float cy = p[1] * x + p[5] * y + p[9] * z + p[13] * w;
      float cz = p[2] * x + p[6] * y + p[10] * z + p[14] * w;
      float cw = p[3] * x + p[7] * y + p[11] * z + p[15] * w;
      if (cw <= 0.001F) {
         return false;
      }

      float ndcX = cx / cw;
      float ndcY = cy / cw;
      float ndcZ = cz / cw;
      if (ndcX < -1.0F || ndcX > 1.0F || ndcY < -1.0F || ndcY > 1.0F || ndcZ < -1.0F || ndcZ > 1.0F) {
         return false;
      }

      nametagX = projViewport.get(0) + (ndcX + 1.0F) * 0.5F * projViewport.get(2);
      nametagY = projViewport.get(1) + (1.0F - ndcY) * 0.5F * projViewport.get(3);
      return true;
      } catch (Throwable t) {
         // A projection failure must never take the frame down: hide the tag.
         return false;
      }
   }

   // ------------------------------------------------------------------
   // Game thread helpers
   // ------------------------------------------------------------------

   private static void onLinkLost() {
      remote = null;
      welcomed = false;
      pendingSpawn = false;
      welcomeSent = false;
      pendingWorldOps.clear();
      if (remoteSubIdx >= 0 && GameScene.getSubmarines() != null && remoteSubIdx < GameScene.getSubmarines().size()) {
         GameScene.getSubmarines().get(remoteSubIdx).applyRemoteIdle();
      }

      remoteMoney = 0;
      remoteSubIdx = -1;
      objSyncX = Float.NaN;
      objSyncZ = Float.NaN;
      objSyncBytes = null;
      droidSyncIdx = -1;
      droidSyncBytes = null;
      waterTimer = 0.0F;
      droidTimer = 0.0F;
      creatureTimer = 0.0F;
      knownEnemies.clear();
      fishTimer = 0.0F;
      knownFish.clear();

      if (role == Role.CLIENT) {
         boolean inGame = isGameplayState() || Main.getGameState() == GameState.LOADING_GAME;
         role = Role.NONE;
         status = Status.ERROR;
         statusText = "Disconnected from host.";
         if (inGame) {
            Main.gameState = GameState.LOADING_MENU;
         }
      } else if (role == Role.HOST) {
         flushClientState();
         status = Status.LISTENING;
         statusText = remoteNickname + " left. Waiting for a player to join...";
      }
   }

   private static void spawnClientAvatar() {
      if (Loading.worldManager == null) {
         return;
      }

      Coord start = Loading.worldManager.getAvatarStartPosition();
      if (start == null) {
         return;
      }

      // Spawn next to the host spawn point so both players do not overlap.
      float x = start.x + 40.0F;
      float z = start.y;
      Point pos = new Point(x, ChunkManager.getHeight(x, z) + 2.0F, z);
      GameScene.avatar.setPos(pos);
      GameScene.avatar.setLastSafeSpot(pos);
   }

   private static boolean isGameplayState() {
      switch (Main.getGameState()) {
         case PLAYING:
         case MAP:
         case INVENTORY:
         case PAUSED:
         case CINEMATIC_INGAME:
         case CINEMATIC_INTRO:
            return true;
         default:
            return false;
      }
   }

   private static void resetState() {
      role = Role.NONE;
      status = Status.OFFLINE;
      statusText = "";
      remote = null;
      welcomed = false;
      pendingSpawn = false;
      welcomeSent = false;
      applyingRemote = false;
      suppressSends = 0;
      sendTimer = 0.0F;
      clientAlive = false;
      eventClientJoined = false;
      eventLinkLost = false;
      pendingPieces = null;
      pendingPlayerState = null;
      bufferedClientState = null;
      handshakeDone = false;
      playerStateTimer = 0.0F;
      nametagVisible = false;
      remoteNickname = "Player";
      incoming.clear();
      outgoing.clear();
      pendingWorldOps.clear();
      if (remoteSubIdx >= 0 && GameScene.getSubmarines() != null && remoteSubIdx < GameScene.getSubmarines().size()) {
         GameScene.getSubmarines().get(remoteSubIdx).applyRemoteIdle();
      }

      remoteMoney = 0;
      remoteSubIdx = -1;
      objSyncX = Float.NaN;
      objSyncZ = Float.NaN;
      objSyncBytes = null;
      droidSyncIdx = -1;
      droidSyncBytes = null;
      waterTimer = 0.0F;
      droidTimer = 0.0F;
      creatureTimer = 0.0F;
      knownEnemies.clear();
      fishTimer = 0.0F;
      knownFish.clear();
      nextFishId = 1;
   }
}
