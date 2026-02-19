# Mineify - Server-Wide Music Player for Minecraft

A Minecraft Fabric mod that enables server-wide music playback where players can search YouTube, add songs to a shared playlist, and listen together.

**Version 1.3.0** — YouTube search and audio downloading are now fully embedded in the mod. No companion service required. **Make sure yt-dlp and ffmpeg are installed on the server, installation guide below**

## Architecture Overview

Everything runs inside the Minecraft server process:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         MINECRAFT SERVER                            │
│  ┌─────────────────┐    ┌──────────────────┐                        │
│  │  Mineify Mod    │◄──►│ Playlist Manager │                        │
│  │  (Server-side)  │    │ (Track Queue)    │                        │
│  └────────┬────────┘    └──────────────────┘                        │
│           │                                                         │
│           │  ┌─────────────────┐  ┌──────────────────────────────┐  │
│           ├─►│ YouTubeService  │  │   AudioDownloadService       │  │
│           │  │ (InnerTube API) │  │  yt-dlp subprocess +         │  │
│           │  └─────────────────┘  │  embedded HTTP file server   │  │
│           │                       └──────────────────────────────┘  │
│           │ Network Packets (playlist sync, play audio URL)         │
│           ▼                                                         │
│  ┌─────────────────┐    ┌──────────────────┐                        │
│  │  Mineify Mod    │───►│  AudioPlayer     │                        │
│  │  (Client-side)  │    │  (WAV via HTTP)  │                        │
│  └─────────────────┘    └──────────────────┘                        │
└─────────────────────────────────────────────────────────────────────┘
```

### How It Works

1. **YouTubeService**: Calls YouTube's internal InnerTube API directly from Java — no API key or npm packages needed.
2. **AudioDownloadService**: Invokes `yt-dlp` as a subprocess to download and convert audio to WAV, then serves the files to clients over an embedded HTTP server (default port 3001).
3. **Audio Playback**: When a song is added, the server downloads it and broadcasts the audio URL to all clients. Each client fetches the WAV file over HTTP and plays it locally using `javax.sound.sampled`.

### Features

- **N Keybinding**: Open the Mineify GUI from anywhere in-game
- **YouTube Search**: Search for songs directly in Minecraft
- **Shared Playlist**: All players see and contribute to the same queue
- **Automatic Playback**: Songs play automatically when added and advance through the queue
- **Audio Sync**: All players start playback at the correct position — download time is compensated so fast and slow downloaders stay in sync. Late joiners automatically seek to where the track currently is rather than starting from the beginning.
- **Session Storage**: Downloads are cached for the duration of the server session
- **No companion service**: Everything is self-contained in the mod JAR

## Project Structure

```
mine-ify/
├── fabric-mod/                 # Minecraft Fabric mod (Java)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/mineify/
│   │   │   │   ├── Mineify.java                  # Main mod entry (server)
│   │   │   │   ├── MineifyConfig.java            # Config loader
│   │   │   │   ├── server/
│   │   │   │   │   ├── PlaylistManager.java      # Server playlist & playback
│   │   │   │   │   ├── YouTubeService.java       # YouTube search (InnerTube API)
│   │   │   │   │   └── AudioDownloadService.java # yt-dlp + embedded HTTP server
│   │   │   │   └── network/
│   │   │   │       ├── MineifyPackets.java       # Packet registration
│   │   │   │       └── packets/
│   │   │   │           ├── SearchRequestPacket.java
│   │   │   │           ├── AddToPlaylistPacket.java
│   │   │   │           ├── SearchResultsPacket.java
│   │   │   │           ├── PlaylistSyncPacket.java
│   │   │   │           ├── PlayAudioPacket.java
│   │   │   │           └── NowPlayingPacket.java
│   │   │   └── resources/
│   │   │       └── fabric.mod.json
│   │   └── client/
│   │       └── java/com/mineify/
│   │           ├── MineifyClient.java            # Client-side entry
│   │           └── client/
│   │               ├── MineifyScreen.java        # Main GUI screen
│   │               ├── MineifyKeybinds.java      # Keybinding handler
│   │               └── audio/
│   │                   └── AudioPlayer.java      # WAV download & playback
│   ├── build.gradle
│   └── gradle.properties
│
└── README.md
```

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.x+
- Fabric API
- Java 21
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) installed and on the **server's** system PATH
- [ffmpeg](https://ffmpeg.org/) installed and on the **server's** system PATH (used by yt-dlp for audio conversion)

Clients only need the mod JAR — no extra tools required.

### Installing yt-dlp and ffmpeg (Windows)

Both tools are required on the server. Choose one of the following methods:

#### Option 1: Package Managers (Recommended)

Using a package manager is the easiest approach as it handles PATH configuration automatically.

**Scoop:**
```
scoop install yt-dlp ffmpeg
```

**Chocolatey:**
```
choco install yt-dlp ffmpeg
```

**Winget:**
```
winget install yt-dlp ffmpeg
```

#### Option 2: Direct Download

**yt-dlp:**
1. Download `yt-dlp.exe` from the [official releases page](https://github.com/yt-dlp/yt-dlp/releases/latest)
2. Place it in a folder (e.g., `C:\yt-dlp`)
3. Add that folder to your system PATH

**ffmpeg:**
1. Download from https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip
2. Extract to a folder (e.g., `C:\ffmpeg`)
3. Add `C:\ffmpeg\bin` to your system PATH

#### Adding to PATH

1. Press `Win + R`, type `sysdm.cpl`, and press Enter
2. Go to **Advanced** → **Environment Variables**
3. Under **System variables**, select `Path` and click **Edit**
4. Click **New** and add the folder path (e.g., `C:\yt-dlp` or `C:\ffmpeg\bin`)
5. Click **OK** on all dialogs
6. Restart your terminal/server for changes to take effect

#### Verify Installation

Open Command Prompt and run:
```
yt-dlp --version
ffmpeg -version
```

Both commands should print version information without errors.

## Installation

### Client Setup

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and place it in your `.minecraft/mods/` folder.
3. Build the mod (or obtain the JAR):
   ```bash
   cd fabric-mod
   ./gradlew build
   ```
4. Copy `fabric-mod/build/libs/mineify-1.3.0.jar` into your `.minecraft/mods/` folder.
5. Launch Minecraft using the Fabric profile. No additional client configuration needed.

**Note:** Clients must be able to reach the server's audio port (default 3001) over HTTP to download audio. If players connect over the internet, make sure port 3001 is open/forwarded on the server and `audioServerUrl` is set to the server's public IP (see Server Setup).

### Server Setup

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) on your server for Minecraft 1.21.11.
2. Install `yt-dlp` and `ffmpeg` on the server machine and ensure both are on the system PATH.
3. Place both the **Fabric API** JAR and the **Mineify** JAR (`mineify-1.3.0.jar`) into the server's `mods/` folder.
4. (Optional) Create `config/mineify.json` in your server directory to customise settings:
   ```json
   {
     "audioServerPort": 3001,
     "audioServerUrl": "http://your-server-ip:3001",
     "downloadDir": "./mineify-downloads",
     "maxPlaylistSize": 50
   }
   ```
   - `audioServerPort` — port the embedded audio server listens on (default `3001`)
   - `audioServerUrl` — URL clients use to fetch audio; **must be reachable from all clients** (default `http://localhost:3001`, which only works for LAN/local play)
   - `downloadDir` — where downloaded WAV files are stored (default `./mineify-downloads`)
5. Start the Minecraft server. The embedded audio server starts automatically alongside it.

## Usage

1. Join a server with Mineify installed
2. Press **N** to open the Mineify GUI
3. Search for a song using the search bar
4. Click a result to add it to the playlist
5. Songs play automatically for all connected players

## Development

Requires Java 21. If your default Java version differs, set `JAVA_HOME`:
```bash
export JAVA_HOME=/path/to/jdk-21
```

```bash
cd fabric-mod
./gradlew build          # Build the mod
./gradlew runClient      # Run test client (opens Minecraft with mod loaded)
./gradlew runServer      # Run headless test server
```

## Legal Notes

This mod is for personal/educational use. Users are responsible for ensuring they comply with YouTube's Terms of Service and applicable copyright laws. The developers do not endorse or encourage piracy.

## License

MIT License - See LICENSE file for details.
