# FarSky Restoration

Unofficial update for [FarSky](https://farsky-interactive.itch.io/farsky) that restores the game and adds **online co-op multiplayer**.

## Features

* **Online multiplayer** — one host + one player, over LAN or the internet.
* **What is synced:**
  * players: position, health / hunger / oxygen, inventory and hotbar
  * world: mined blocks, ores, harvested plants, dropped items
  * bases, chests, tombs, plant pots, submarine pieces
  * droids: state, inventory and commands; submarines: spawn and driving
  * water level, day/night clock, money HUD
  * in-game chat
  * nicknames drawn above player sprites
* **Not synced yet (for now): AI** — fish and other creatures run separately on each side.

Everything is saved on the host: worlds in `save/`, per-player progress in `save/players/`. Joining players keep **no local save files**.

## Download and run

1. Download FarSky from [itch.io](https://farsky-interactive.itch.io/farsky) — you need to own the game (this project does not include any game assets).
2. Install [Java 17 or newer](https://adoptium.net/).
3. Download **[farsky-restoration.zip](https://github.com/a1elen/farsky-restoration/releases/download/v1.0/farsky-restoration.zip)** (4.6 MB, also on the [Releases page](https://github.com/a1elen/farsky-restoration/releases)).
4. Copy everything from the zip into the FarSky game folder (merge/overwrite).
5. Double-click **Play FarSky.bat** — done. Use **Play FarSky (windowed).bat** if you prefer a window.

## How to play multiplayer

1. **Host**: open *Multiplayer*, set a nickname, click one of the three world slot buttons (World 1/2/3).
2. **Join**: open *Multiplayer*, set a nickname, type the host's address (LAN or public IP), click *Join game*.
3. TCP port **45678** must be reachable by the joining player — port-forward on the host, or play over LAN / a VPN tunnel (Tailscale, Hamachi, ...).

## Building from source

1. Copy the game resources from your itch.io copy into `farsky-app/src/main/resources` (`obj/`, `sounds/`, `textures/`).
2. `./gradlew run`

## Thanks

* The original **FarSky** was made by its developers back in 2014 — thank you for an amazing game!
* [AlbatorLaho](https://github.com/AlbatorLaho) for the original decompile this project is based on.
