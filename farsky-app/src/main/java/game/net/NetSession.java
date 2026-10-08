package game.net;

import game.Main;
import game.chunks.ChunkManager;
import game.enemy.EnemyGenerator;
import game.inventory.ItemType;
import game.manager.GameMode;
import game.manager.GameScene;
import game.manager.GameState;
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
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Minimal TCP session for two players (host + client).
 *
 * <p>Protocol: frames of {@code int length} + {@code byte[length] payload},
 * first payload byte is the message type:</p>
 * <ul>
 *   <li>{@link #MSG_HELLO}    client -> host: protocol version</li>
 *   <li>{@link #MSG_WELCOME}  host -> client: world seed and settings, so the
 *       client can deterministically rebuild the same world</li>
 *   <li>{@link #MSG_POS}      both: player position and yaw at 20 Hz</li>
 * </ul>
 *
 * <p>All game state changes happen on the game thread inside {@link #update(float)};
 * network threads only move bytes and set volatile flags.</p>
 */
public final class NetSession {
   public enum Role { NONE, HOST, CLIENT }
   public enum Status { OFFLINE, LISTENING, CONNECTING, CONNECTED, ERROR }

   public static final int DEFAULT_PORT = 45678;
   private static final int PROTOCOL_VERSION = 2;
   private static final byte MSG_HELLO = 1;
   private static final byte MSG_WELCOME = 2;
   private static final byte MSG_POS = 3;
   private static final byte MSG_CHEST_TAKEN = 4;
   private static final byte MSG_PLANT_HARVEST = 5;
   private static final byte MSG_ORE_DEPOSIT = 6;
   private static final byte MSG_ORE_MINED = 7;
   private static final byte MSG_BASE_SPAWN = 8;
   private static final byte MSG_BLOCK_OP = 9;
   private static final int MAX_FRAME_SIZE = 33554432;
   private static final float SEND_INTERVAL = 0.05F;
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

   public static int port = DEFAULT_PORT;
   public static String joinAddress = "127.0.0.1";

   private static volatile boolean running = false;
   private static volatile boolean clientAlive = false;
   private static volatile boolean eventClientJoined = false;
   private static volatile boolean eventLinkLost = false;

   private static volatile ServerSocket serverSocket;
   private static volatile Socket activeSocket;
   private static volatile DataOutputStream activeOut;

   private static final ConcurrentLinkedQueue<byte[]> incoming = new ConcurrentLinkedQueue<byte[]>();
   private static final ConcurrentLinkedQueue<byte[]> outgoing = new ConcurrentLinkedQueue<byte[]>();

   private static RemotePlayer remote = null;
   private static boolean welcomed = false;
   private static boolean pendingSpawn = false;
   private static float sendTimer = 0.0F;
   private static boolean welcomeSent = false;
   private static boolean applyingRemote = false;
   private static int suppressSends = 0;
   private static final ArrayList<byte[]> pendingWorldOps = new ArrayList<byte[]>();

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

   /** Starts listening for one client and creates a fresh adventure world. */
   public static synchronized void host() {
      if (role != Role.NONE) {
         return;
      }

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

      running = false;
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

      if (eventClientJoined) {
         eventClientJoined = false;
         status = Status.CONNECTED;
         statusText = role == Role.HOST ? "Player connected." : "Connected. Receiving world data...";
      }

      // The welcome with the world snapshot must be built on the game thread.
      if (role == Role.HOST && status == Status.CONNECTED && !welcomeSent && activeOut != null) {
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
         }
      }

      if (pendingSpawn && Main.getGameState() == GameState.PLAYING && GameScene.avatar != null) {
         pendingSpawn = false;
         spawnClientAvatar();
      }

      if (remote != null) {
         remote.update(delta);
      }
   }

   /** Renders the remote player; call from the world render pass with the enemy shader bound. */
   public static void renderRemote() {
      if (remote != null && status == Status.CONNECTED) {
         remote.render();
      }
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
      activeSocket = s;
      activeOut = new DataOutputStream(s.getOutputStream());
      incoming.clear();
      outgoing.clear();
      pendingWorldOps.clear();
      clientAlive = true;
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
            case MSG_WELCOME:
               if (role == Role.CLIENT) {
                  handleWelcome(d);
               }

               break;
            case MSG_POS:
               float x = d.readFloat();
               float y = d.readFloat();
               float z = d.readFloat();
               float yaw = d.readFloat();
               if (remote == null) {
                  remote = new RemotePlayer();
               }

               remote.setTarget(x, y, z, yaw);
               break;
            case MSG_CHEST_TAKEN:
            case MSG_PLANT_HARVEST:
            case MSG_ORE_DEPOSIT:
            case MSG_ORE_MINED:
            case MSG_BASE_SPAWN:
            case MSG_BLOCK_OP:
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

      World world;
      ArrayList<SeafloorBase> bases;
      try {
         world = (World)deserialize(worldBytes);
         bases = (ArrayList<SeafloorBase>)deserialize(baseBytes);
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
      pendingSpawn = true;
      Main.gameState = GameState.LOADING_GAME;
   }

   private static void sendPosition() {
      Point pos = GameScene.avatar.getPos();
      ByteArrayOutputStream bos = new ByteArrayOutputStream(17);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_POS);
         d.writeFloat(pos.x);
         d.writeFloat(pos.y);
         d.writeFloat(pos.z);
         d.writeFloat(GameScene.avatar.getHorizontalAngle());
      } catch (IOException e) {
         return;
      }

      queueRaw(bos.toByteArray());
   }

   private static byte[] buildHello() {
      ByteArrayOutputStream bos = new ByteArrayOutputStream(5);
      DataOutputStream d = new DataOutputStream(bos);

      try {
         d.writeByte(MSG_HELLO);
         d.writeInt(PROTOCOL_VERSION);
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
         d.writeByte(WorldManager.spawning.ordinal());

         // World snapshot: late joiners inherit opened chests, mined ore, harvested plants.
         World hostWorld = Loading.worldManager != null ? Loading.getPendingWorld() : null;
         byte[] worldBytes = serialize(hostWorld);
         d.writeInt(worldBytes.length);
         d.write(worldBytes);

         ArrayList<SeafloorBase> bases = GameScene.getSeafloorBases();
         byte[] baseBytes = serialize(bases == null ? new ArrayList<SeafloorBase>() : new ArrayList<SeafloorBase>(bases));
         d.writeInt(baseBytes.length);
         d.write(baseBytes);
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

   // ------------------------------------------------------------------
   // Game thread helpers
   // ------------------------------------------------------------------

   private static void onLinkLost() {
      remote = null;
      welcomed = false;
      pendingSpawn = false;
      welcomeSent = false;
      pendingWorldOps.clear();

      if (role == Role.CLIENT) {
         boolean inGame = isGameplayState() || Main.getGameState() == GameState.LOADING_GAME;
         role = Role.NONE;
         status = Status.ERROR;
         statusText = "Disconnected from host.";
         if (inGame) {
            Main.gameState = GameState.LOADING_MENU;
         }
      } else if (role == Role.HOST) {
         status = Status.LISTENING;
         statusText = "Player left. Waiting for a player to join...";
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
      incoming.clear();
      outgoing.clear();
      pendingWorldOps.clear();
   }
}
