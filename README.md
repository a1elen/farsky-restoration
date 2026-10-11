# FarSky Restoration Multiplayer

Unofficial update for [FarSky](https://farsky-interactive.itch.io/farsky) that restores the game and adds **online co-op multiplayer**.
NOT TESTED FULLY

## Features

* **Online multiplayer** — one host + up to 7 other players (8 in total), over LAN or the internet.
* **What is synced:**
  * players: position, health / hunger / oxygen, inventory and hotbar; coins are per player, saved with them on the host
  * world: mined blocks, ores, harvested plants, dropped items
  * bases, chests, tombs, plant pots, submarine pieces
  * droids: state, inventory and commands; submarines: spawn and driving
  * water level, day/night clock
  * creatures: hostile AI and fish schools (run by the host, mirrored live on every side, smoothly interpolated)
  * in-game chat with `*player* connected / disconnected` notices
  * nicknames drawn above player sprites
  * player list with ping and coins in the pause menu (Esc)

Everything is saved on the host: worlds in `save/`, per-player progress in `save/players/<world>_<nickname>.sav`. Joining players keep **no local save files** — their inventory and coins follow their nickname on the host.

* **Menus no longer pause the game** — the inventory, map and Esc menus overlay the live world: enemies, hunger and the clock keep running.
* **Time of day under the minimap** — day counter, clock and a day/night phase label.
* **Map controls** — drag with the left mouse button to pan, with the right mouse button to rotate; WASD and the mouse wheel still work.
* **Multiplayer menu** — the `X` button next to each world slot deletes that save (with a confirmation step).

* **Graphics options** — individually toggleable in *Options → Graphics*.
  * Shadows with a sun elevation that follows the day/night cycle, so shadows lengthen and shift as the day passes.
  * Screen space ambient occlusion, cleaned up with a depth aware blur so silhouettes stay sharp instead of turning grainy.
  * New Screen setting: Fullscreen, Windowed or Borderless.
  * Also added: FXAA / MSAA 4x, bloom, vignette, motion blur and color grading.
* **Borderless windowed mode** — *Options → Graphics → Screen* offers *Fullscreen*, *Windowed* and *Borderless*.

## Download and run

1. Download FarSky from [itch.io](https://farsky-interactive.itch.io/farsky) — you need to own the game (this project does not include any game assets).
2. Install [Java 17 or newer](https://adoptium.net/).
3. Download **[farsky-restoration.zip](https://github.com/a1elen/farsky-restoration-multiplayer/releases/download/v1.3/farsky-restoration.zip)** (also on the [Releases page](https://github.com/a1elen/farsky-restoration-multiplayer/releases)).
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
* AI for making all the heavy work
