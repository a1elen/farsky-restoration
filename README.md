# FarSky Restoration (unofficial)

This is an unofficial effort to restore and reconstruct the [FarSky](https://www.farskyinteractive.com/farsky) video game.

Why, you might ask?
Because it's [basically abandoned](https://steamcommunity.com/games/286340/announcements/detail/3087787366398826689).
The owner also made [comments on itch.io](https://farsky-interactive.itch.io/farsky#post-7729741) indicating that he might open source it himself at some point, but said:
> I am not ready for this yet, plus I don't want any issue with the musician (Lapse) who did the musics.

I've decompiled and deobfuscated most of the code. (with the help of AI)
I plan to update, refactor, and modernize, the code and its dependencies.
Hopefully I (or others) will be able to add new features as well! (like multiplayer or modding)

## What's new: online co-op multiplayer

This build adds two-player online co-op on top of the vanilla game:

* **Proper mouse-driven multiplayer menu** - like the default game menus: set
  your nickname (required, defaults are `Host` for the hosting side and
  `Client` for the joining side), enter the host address, click buttons.
* **Three world slots, no cycling** - the host clicks a slot button
  (World 1/2/3) and hosts that save directly.
* **Everything is saved on the host** - joining players keep **no local save
  files at all**. Your position, inventory, hotbar and vitals are stored per
  nickname in `save/players/<world>_<nick>.sav` on the host's machine and are
  sent back to you every time you rejoin.
* **Nicknames above players** - nearby players see each other's name drawn
  over the character sprite.
* **State sync** - world edits, bases, doors, plant pots, money HUD,
  extractor/droid inventories and commands, submarines, water snapshots,
  the day/night clock and chat stay in sync between host and client.

Networking is plain TCP on port **45678**.

## Initial setup

I haven't bundled the game resources, since I don't have permission to distribute them.
Though technically I didn't have permission to decompile and refactor the code either...
But that being said, you'll have to copy the game resources from the [official game on itch.io](https://farsky-interactive.itch.io/farsky).

After you've downloaded the game, rename `farsky.jar` to `farsky.zip` and extract it.
Inside should be a `res` folder.
Copy its **_contents_** to `farsky-app/src/main/resources`.
It should look like `resources/sounds` rather than `resources/res/sounds`.

Then copy the `native` folder to the project root, alongside the `farsky-app` folder.
If you're on macOS, you may need to bypass quarantine for all files under `native/macosx`.

At this point it should be ready to build!

## Build

```sh
./gradlew build
```

## Run

```sh
./gradlew run
```

Starts the game windowed against the `_FarSky/` data folder.

Command-line flags also work when running a built jar directly:

```text
-windowMode            start in a window (fullscreen by default)
-path:<dir>            data directory for saves (default: current folder)
-logPath:<dir>         where farsky.log is written (default: current folder)
-mpHost                auto-host world slot 1 and wait for a player
-mpJoin:<address>      auto-join the given host
```

## Package for players (overlay for an itch.io copy)

The repository intentionally contains **no original game assets**. To play,
you must own FarSky and download it from [itch.io](https://farsky-interactive.itch.io/farsky).

Build the redistributable overlay package:

```powershell
powershell -ExecutionPolicy Bypass -File tools\make-dist.ps1
```

This produces `dist/farsky-restoration.zip` containing the compiled game,
LWJGL natives, launch scripts and a readme - and **strips every original
asset out of the jar**, so the package never redistributes anything
copyrighted.

To install the package:

1. Install [Java 17 or newer](https://adoptium.net/).
2. Unpack the purchased FarSky download anywhere.
3. Copy the contents of `farsky-restoration.zip` into that folder
   (merge/overwrite).
4. Start the game with `Play FarSky.bat`
   (or `Play FarSky (windowed).bat` for a window).

The launcher picks the original assets up automatically - either straight
from the purchased `farsky.jar` next to it, or from a `res/` folder (rename
`farsky.jar` to `farsky.zip` and extract it). No assets have to be copied
around. Saves are written to the `save/` folder next to the game.

## Playing multiplayer

1. **Host**: open *Multiplayer*, set a nickname, then click one of the three
   world slot buttons to host it.
2. **Join**: open *Multiplayer*, set a nickname, type the host's address
   (LAN IP or public IP) and click *Join game*.
3. Progress lives on the host: worlds in `save/`, per-player files in
   `save/players/` keyed by nickname. The joining player's machine keeps no
   save files.

Port `45678/tcp` must be reachable by the joining player (port forwarding on
the host, or play over LAN / a VPN tunnel such as Hamachi or Tailscale).

## Shout-out

Huge thanks to the amazing people that made this game back in 2014!
It was truly an amazing game that had a lot of potential!
