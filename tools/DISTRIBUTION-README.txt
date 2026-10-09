FarSky Restoration - multiplayer build
======================================

This package contains ONLY the modified game code (compiled classes,
libraries, native drivers and launch scripts). It does NOT include any of
the original FarSky assets - textures, sounds, models or music. You need to
own the game and download it from https://farsky-interactive.itch.io/farsky

INSTALLATION
------------
1. Install Java 17 or newer if you don't have it: https://adoptium.net/
2. Download FarSky from itch.io and unpack it anywhere you like.
3. Copy EVERYTHING from this package into that folder (merge/overwrite).
   The folder should now contain "Play FarSky.bat" next to the original
   farsky.jar (or next to the res/ folder extracted from it).
4. Double-click "Play FarSky.bat" to play
   (use "Play FarSky (windowed).bat" if you prefer a window).

RUNNING
-------
The launcher finds the original assets automatically - either straight from
the purchased farsky.jar in the same folder, or from a res/ folder (just
rename farsky.jar to farsky.zip and extract it). You don't have to copy any
assets around.

Saves and options are written to the save/ folder and options.sav next to
the game.

MULTIPLAYER
-----------
Main menu -> Multiplayer:

  * Set a nickname first - it is required, is shown above your character,
    and is used for YOUR progress file. Empty nicknames and "Player" are not
    accepted (defaults are "Host" and "Client").
  * Host: click one of the three world slot buttons (World 1/2/3) to host
    that save directly.
  * Join: type the host's address (LAN or public IP) and click Join game.

Command line (optional):
  "Play FarSky.bat" -mpHost            auto-host world slot 1
  "Play FarSky.bat" -mpJoin:1.2.3.4    auto-join the given host

Network: TCP port 45678 must be reachable by the joining player (port
forwarding on the host, or play over LAN / a VPN tunnel such as
Hamachi or Tailscale).

Saves in multiplayer: the host keeps all worlds in save/ and every player's
progress in save/players/. Joining clients keep NO local save files - your
position, inventory and vitals are stored on the host under your nickname
and are sent back to you every time you rejoin.

TROUBLESHOOTING
---------------
* "Java ... was not found"  -> install Java 17+ and try again.
* Crash, black screen etc.  -> check farsky.log in this folder.
* Instant close on startup  -> run the game from a terminal to see the
                               error: open a command prompt in this folder
                               and run:  "Play FarSky.bat" -windowMode
