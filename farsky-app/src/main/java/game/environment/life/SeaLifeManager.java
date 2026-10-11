package game.environment.life;

import game.chunks.ChunkManager;
import game.collision.BoundingBox;
import game.manager.Camera;
import game.manager.GameScene;
import game.manager.Loading;
import game.player.damage.Damage;
import game.shader.Shaders;
import game.util.Coord;
import game.util.Point;
import game.util.Segment;
import game.util.State;
import java.util.ArrayList;
import org.lwjgl.opengl.GL11;

public final class SeaLifeManager {
   private ArrayList<Fish> skatefishes = new ArrayList<>();
   private ArrayList<Fish> tunas = new ArrayList<>();
   private ArrayList<Fish> dolphins = new ArrayList<>();
   private ArrayList<Fish> whales = new ArrayList<>();
   private ArrayList<Fish> standardFishes = new ArrayList<>();
   private float spawnTimer;
   private static float SPAWN_INTERVAL = 3.0F;
   private ArrayList<Coord> spawnedChunks = new ArrayList<>();

   public SeaLifeManager() {
      this.spawnTimer = SPAWN_INTERVAL;

      // The client receives its fish from the host instead of breeding them.
      if (game.net.NetSession.isClient()) {
         return;
      }

      for (float x = -ChunkManager.viewDistance; x <= ChunkManager.viewDistance; x += 128.0F) {
         for (float z = -ChunkManager.viewDistance; z <= ChunkManager.viewDistance; z += 128.0F) {
            Point p = Camera.getPosition().plus(x, 0.0F, z);
            this.spawnFishAt(p.toCoord());
         }
      }
   }

   public final void update(float delta) {
      int stage = 0;
      if (GameScene.avatar != null) {
         stage = Loading.worldManager.getStageAt(Camera.getPosition().x, Camera.getPosition().z);
      }

      for (int i = 0; i < this.skatefishes.size(); i++) {
         this.skatefishes.get(i).tick(delta);
      }

      for (int i = 0; i < this.tunas.size(); i++) {
         this.tunas.get(i).tick(delta);
      }

      for (int i = 0; i < this.dolphins.size(); i++) {
         this.dolphins.get(i).tick(delta);
      }

      for (int i = 0; i < this.standardFishes.size(); i++) {
         this.standardFishes.get(i).tick(delta);
      }

      for (int i = 0; i < this.whales.size(); i++) {
         this.whales.get(i).tick(delta);
      }

      if (!game.net.NetSession.isClient()) {
         this.spawnPeriodic(delta, stage);
      }

      this.cleanup(this.skatefishes);
      this.cleanup(this.tunas);
      this.cleanup(this.dolphins);
      this.cleanup(this.standardFishes);
      this.cleanup(this.whales);

      if (!game.net.NetSession.isClient()) {
         this.assignNetIds();
      }

      for (int i = this.spawnedChunks.size() - 1; i >= 0; i--) {
         if (this.spawnedChunks.get(i).distanceTo(Camera.getPosition().toCoord()) > ChunkManager.viewDistance * 1.2F) {
            this.spawnedChunks.remove(i);
         }
      }
   }

   /** Host: everything that breeds new fish - never run on a mirroring client. */
   private void spawnPeriodic(float delta, int stage) {
      this.spawnTimer += delta;
      if (this.spawnTimer < SPAWN_INTERVAL) {
         return;
      }

      this.spawnTimer = this.spawnTimer - SPAWN_INTERVAL;
      this.spawnAround(Camera.getPosition(), Camera.getYaw(), stage);

      // Every connected player gets a population around them too, so nobody is
      // left swimming through an empty sea when the others are far away.
      java.util.ArrayList<game.net.RemotePlayer> remotes = game.net.NetSession.getRemotePlayers();
      for (int i = 0; i < remotes.size(); i++) {
         game.net.RemotePlayer peer = remotes.get(i);
         if (peer.getRenderPos().distanceTo(Camera.getPosition()) > ChunkManager.viewDistance) {
            int peerStage = Loading.worldManager == null
                  ? 0 : Loading.worldManager.getStageAt(peer.getRenderPos().x, peer.getRenderPos().z);
            this.spawnAround(peer.getRenderPos(), peer.getRenderYaw(), peerStage);
         }
      }
   }

   /** Host: one spawn pass around an anchor (the camera, or the joined player). */
   private void spawnAround(Point anchor, float yaw, int stage) {
      float startAngle = (float)(Math.random() * Math.PI * 2.0);
      for (float angle = startAngle; angle < startAngle + Math.PI * 2.0; angle = (float)(angle + (Math.PI / 16))) {
         Point spawnPoint = anchor.plus(new Point(Math.cos(angle) * ChunkManager.viewDistance, 0.0, Math.sin(angle) * ChunkManager.viewDistance));
         this.spawnFishAt(spawnPoint.toCoord());
      }

      if (stage == 0 && Math.random() < 0.08F) {
         float yawOffset = ((float)Math.random() - 0.5F) * 90.0F;
         Coord spawnDir = new Coord(Math.sin(Math.toRadians(yaw + yawOffset)), Math.cos(Math.toRadians(yaw + yawOffset)));

         for (int i = 0; i < 5; i++) {
            Point p = anchor
                  .plus(new Point(-spawnDir.x * (ChunkManager.viewDistance + Math.random() * 210.0), 0.0, -spawnDir.y * (ChunkManager.viewDistance + Math.random() * 210.0)));
            p.y = ChunkManager.getHeight(p.x, p.z) + 250.0F;
            this.skatefishes.add(new Skatefish(p, spawnDir));
         }
      }

      if (stage == 0 && Math.random() < 0.05F || stage == 1 && Math.random() < 0.03F) {
         float yawOffset = ((float)Math.random() - 0.5F) * 90.0F;
         Coord spawnDir = new Coord(Math.sin(Math.toRadians(yaw + yawOffset)), Math.cos(Math.toRadians(yaw + yawOffset)));

         for (int i = 0; i < 15; i++) {
            Point p = anchor
                  .plus(new Point(-spawnDir.x * (ChunkManager.viewDistance + Math.random() * 210.0), 0.0, -spawnDir.y * (ChunkManager.viewDistance + Math.random() * 210.0)));
            p.y = ChunkManager.getHeight(p.x, p.z) + 250.0F;
            this.tunas.add(new Tuna(p, spawnDir));
         }
      }

      if (stage == 0 && Math.random() < 0.006F) {
         float yawOffset = ((float)Math.random() - 0.5F) * 90.0F;
         Coord spawnDir = new Coord(Math.sin(Math.toRadians(yaw + yawOffset)), Math.cos(Math.toRadians(yaw + yawOffset)));

         for (int i = 0; i < 15; i++) {
            Point p = anchor
                  .plus(new Point(-spawnDir.x * (ChunkManager.viewDistance + Math.random() * 210.0), 0.0, -spawnDir.y * (ChunkManager.viewDistance + Math.random() * 210.0)));
            p.y = ChunkManager.getHeight(p.x, p.z) + 250.0F;
            this.dolphins.add(new Dolphin(p, spawnDir));
         }
      }

      if (stage == 0 && Math.random() < 0.01F) {
         float yawOffset = ((float)Math.random() - 0.5F) * 90.0F;
         Coord spawnDir = new Coord(Math.sin(Math.toRadians(yaw + yawOffset)), Math.cos(Math.toRadians(yaw + yawOffset)));

         for (int i = 0; i <= 0; i++) {
            Point p = anchor
                  .plus(new Point(-spawnDir.x * (ChunkManager.viewDistance + Math.random() * 210.0), 0.0, -spawnDir.y * (ChunkManager.viewDistance + Math.random() * 210.0)));
            p.y = ChunkManager.getHeight(p.x, p.z) + 250.0F;
            this.whales.add(new Whale(p, spawnDir));
         }
      }
   }

   /** Host: drops fish that swam out of range or died, and tells the client. */
   private void cleanup(ArrayList<Fish> list) {
      for (int i = list.size() - 1; i >= 0; i--) {
         Fish fish = list.get(i);
         if (fish.shouldRemove()) {
            if (fish.getNetId() >= 0) {
               game.net.NetSession.sendFishDespawn(fish.getNetId());
            }

            list.remove(i);
         }
      }
   }

   /** Host: gives every freshly spawned fish a stable id before it is broadcast. */
   private void assignNetIds() {
      this.assignNetIds(this.skatefishes);
      this.assignNetIds(this.tunas);
      this.assignNetIds(this.dolphins);
      this.assignNetIds(this.standardFishes);
      this.assignNetIds(this.whales);
   }

   private void assignNetIds(ArrayList<Fish> list) {
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).getNetId() < 0) {
            list.get(i).setNetId(game.net.NetSession.nextFishId());
         }
      }
   }

   public final void render() {
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      Skatefish.setupDraw();

      for (int i = 0; i < this.skatefishes.size(); i++) {
         this.skatefishes.get(i).draw();
      }

      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      Tuna.setupDraw();

      for (int i = 0; i < this.tunas.size(); i++) {
         this.tunas.get(i).draw();
      }

      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      Dolphin.setupDraw();

      for (int i = 0; i < this.dolphins.size(); i++) {
         this.dolphins.get(i).draw();
      }

      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      Whale.setupDraw();

      for (int i = 0; i < this.whales.size(); i++) {
         this.whales.get(i).draw();
      }

      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
      if (this.standardFishes.size() > 0) {
         FishType currentType = this.standardFishes.get(0).getFishType();
         Fish.setupDraw(currentType);

         for (int i = 0; i < this.standardFishes.size(); i++) {
            if (currentType != this.standardFishes.get(i).getFishType()) {
               currentType = this.standardFishes.get(i).getFishType();
               Fish.setupDraw(currentType);
            }

            this.standardFishes.get(i).draw();
         }
      }

      Shaders.setUniform("axis", new Point(0.0F, 0.0F, 0.0F));
      Shaders.setUniform("wave", new Point(0.0F, 0.0F, 0.0F));
      Shaders.setUniform("alphaLightPercent", 0.0);
      Shaders.setUniform("offset", 0.0);
      GL11.glDepthMask(true);
      GL11.glEnable(GL11.GL_CULL_FACE);
      Shaders.setUniform("invertAlphaLight", false);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
   }

   /** Fish classes packed into MSG_FISH_SYNC. */
   public static final int KIND_STANDARD = 0;
   public static final int KIND_ABYSSAL = 1;
   public static final int KIND_SKATEFISH = 2;
   public static final int KIND_TUNA = 3;
   public static final int KIND_DOLPHIN = 4;
   public static final int KIND_WHALE = 5;

   /** Host: which packed kind a fish belongs to. */
   public static int kindOf(Fish fish) {
      if (fish instanceof Skatefish) {
         return KIND_SKATEFISH;
      }

      if (fish instanceof Tuna) {
         return KIND_TUNA;
      }

      if (fish instanceof Dolphin) {
         return KIND_DOLPHIN;
      }

      if (fish instanceof Whale) {
         return KIND_WHALE;
      }

      if (fish instanceof AbyssalFish) {
         return KIND_ABYSSAL;
      }

      return KIND_STANDARD;
   }

   /** Client: builds a fish from a host sample; it never runs schooling AI. */
   public final Fish spawnRemote(int id, int kind, FishType type, Point pos, float rotX, float rotY) {
      Fish fish;
      Coord flatDir = new Coord(1.0F, 0.0F);

      switch (kind) {
         case KIND_SKATEFISH:
            fish = new Skatefish(pos, flatDir);
            break;
         case KIND_TUNA:
            fish = new Tuna(pos, flatDir);
            break;
         case KIND_DOLPHIN:
            fish = new Dolphin(pos, flatDir);
            break;
         case KIND_WHALE:
            fish = new Whale(pos, flatDir);
            break;
         case KIND_ABYSSAL:
            fish = new AbyssalFish(pos, type);
            break;
         default:
            fish = new StandardFish(pos, type);
            break;
      }

      fish.setNetId(id);
      fish.beginRemoteControl();
      fish.applyNetState(pos.x, pos.y, pos.z, rotX, rotY);
      this.listOf(fish).add(fish);
      return fish;
   }

   /** The fish carrying this host id, or null when it is not in the world. */
   public final Fish getByNetId(int id) {
      Fish fish = findIn(this.skatefishes, id);
      if (fish == null) {
         fish = findIn(this.tunas, id);
      }

      if (fish == null) {
         fish = findIn(this.dolphins, id);
      }

      if (fish == null) {
         fish = findIn(this.whales, id);
      }

      if (fish == null) {
         fish = findIn(this.standardFishes, id);
      }

      return fish;
   }

   /** Client: drops a fish the host removed (by explicit despawn message). */
   public final void removeRemote(int id) {
      removeById(this.skatefishes, id);
      removeById(this.tunas, id);
      removeById(this.dolphins, id);
      removeById(this.whales, id);
      removeById(this.standardFishes, id);
   }

   /** Fills {@code out} with every live fish, for the host's state broadcast. */
   public final void collectAll(ArrayList<Fish> out) {
      out.addAll(this.skatefishes);
      out.addAll(this.tunas);
      out.addAll(this.dolphins);
      out.addAll(this.whales);
      out.addAll(this.standardFishes);
   }

   private ArrayList<Fish> listOf(Fish fish) {
      if (fish instanceof Skatefish) {
         return this.skatefishes;
      }

      if (fish instanceof Tuna) {
         return this.tunas;
      }

      if (fish instanceof Dolphin) {
         return this.dolphins;
      }

      if (fish instanceof Whale) {
         return this.whales;
      }

      return this.standardFishes;
   }

   private static Fish findIn(ArrayList<Fish> list, int id) {
      if (id < 0) {
         return null;
      }

      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).getNetId() == id) {
            return list.get(i);
         }
      }

      return null;
   }

   private static void removeById(ArrayList<Fish> list, int id) {
      if (id < 0) {
         return;
      }

      for (int i = list.size() - 1; i >= 0; i--) {
         if (list.get(i).getNetId() == id) {
            list.remove(i);
         }
      }
   }

   private void spawnFishAt(Coord coord) {
      for (int i = 0; i < this.spawnedChunks.size(); i++) {
         if (this.spawnedChunks.get(i).distanceTo(coord) < 400.0F) {
            return;
         }
      }

      Point center = new Point(coord.x, 0.0F, coord.y);
      FishType fishType = FishType.FISH;
      int stage = 0;
      ArrayList<Fish> fishList = new ArrayList<>();
      if (GameScene.avatar != null) {
         stage = Loading.worldManager.getStageAt(coord.x, coord.y);
      }

      if (stage == 0) {
         switch ((int)(Math.random() * 4.0)) {
            case 0:
               fishType = FishType.FISH;
               break;
            case 1:
               fishType = FishType.BARRACUDA;
               break;
            case 2:
               fishType = FishType.SHARK;
               break;
            case 3:
               fishType = FishType.MANTA_RAY;
         }

         for (int i = 0; i < 20; i++) {
            fishList.add(new StandardFish(center, fishType));
         }
      } else if (stage == 1) {
         switch ((int)(Math.random() * 3.0)) {
            case 0:
               fishType = FishType.FRILLED_SHARK;
               break;
            case 1:
               fishType = FishType.ANGLERFISH;
               break;
            case 2:
               fishType = FishType.WHALE;
         }

         for (int i = 0; i < 25; i++) {
            fishList.add(new StandardFish(center, fishType));
         }
      } else {
         if (stage != 2) {
            return;
         }

         switch ((int)(Math.random() * 2.0)) {
            case 0:
               fishType = FishType.TUNA;
               break;
            case 1:
               fishType = FishType.DOLPHIN;
         }

         for (int i = 0; i < 30; i++) {
            fishList.add(new AbyssalFish(center, fishType));
         }
      }

      boolean inserted = false;

      for (int i = 0; i < this.standardFishes.size(); i++) {
         if (this.standardFishes.get(i).getFishType() == fishType) {
            this.standardFishes.addAll(i, fishList);
            inserted = true;
            break;
         }
      }

      if (!inserted) {
         this.standardFishes.addAll(fishList);
      }

      this.spawnedChunks.add(coord);
   }

   public final Damage applyHit(ArrayList<Segment> segments, Damage weaponDamage) {
      Damage totalDamage = new Damage();

      for (int i = this.skatefishes.size() - 1; i >= 0; i--) {
         totalDamage.accumulate(this.hitAndReport(this.skatefishes.get(i), segments, weaponDamage));
      }

      for (int i = this.tunas.size() - 1; i >= 0; i--) {
         totalDamage.accumulate(this.hitAndReport(this.tunas.get(i), segments, weaponDamage));
      }

      for (int i = this.dolphins.size() - 1; i >= 0; i--) {
         totalDamage.accumulate(this.hitAndReport(this.dolphins.get(i), segments, weaponDamage));
      }

      for (int i = this.whales.size() - 1; i >= 0; i--) {
         totalDamage.accumulate(this.hitAndReport(this.whales.get(i), segments, weaponDamage));
      }

      for (int i = this.standardFishes.size() - 1; i >= 0; i--) {
         totalDamage.accumulate(this.hitAndReport(this.standardFishes.get(i), segments, weaponDamage));
      }

      if (totalDamage.getAmount() > 0.0F) {
         for (int i = 0; i < this.skatefishes.size(); i++) {
            this.skatefishes.get(i).fleeFrom(totalDamage.getSource(), true);
         }

         for (int i = 0; i < this.tunas.size(); i++) {
            this.tunas.get(i).fleeFrom(totalDamage.getSource(), true);
         }

         for (int i = 0; i < this.dolphins.size(); i++) {
            this.dolphins.get(i).fleeFrom(totalDamage.getSource(), true);
         }

         for (int i = 0; i < this.whales.size(); i++) {
            this.whales.get(i).fleeFrom(totalDamage.getSource(), true);
         }

         for (int i = 0; i < this.standardFishes.size(); i++) {
            this.standardFishes.get(i).fleeFrom(totalDamage.getSource(), true);
         }
      }

      return totalDamage;
   }

   /** Runs a hit and, on a mirrored fish, tells the host to apply it for real. */
   private Damage hitAndReport(Fish fish, ArrayList<Segment> segments, Damage weaponDamage) {
      Damage result = fish.checkHit(segments, weaponDamage);
      if (fish.isRemoteControlled() && result.getAmount() > 0.0F) {
         game.net.NetSession.sendFishHit(fish.getNetId(), weaponDamage.getAmount());
      }

      return result;
   }

   public final State resolveCollision(State position, State velocity) {
      for (int i = 0; i < this.whales.size(); i++) {
         velocity = this.whales.get(i).getWorldBoundingBox().resolveCollision(position, velocity);
         if (BoundingBox.groundHit) {
            velocity.vel = new Point();
            velocity.vel.add(this.whales.get(i).getVelocity());
            velocity.pos.add(velocity.vel);
         }
      }

      return velocity;
   }
}
