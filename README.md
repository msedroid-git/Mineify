# Mineify - Server-Wide Music Player for Minecraft

A Minecraft Fabric mod that enables server-wide music playback where players can search YouTube, add songs to a shared playlist, and listen together.

## Architecture Overview

Mineify consists of two main components:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         MINECRAFT SERVER                            │
│  ┌─────────────────┐    ┌──────────────────┐                       │
│  │  Mineify Mod    │◄──►│  Playlist Manager │                      │
│  │  (Server-side)  │    │  (Track Queue)    │                      │
│  └────────┬────────┘    └──────────────────┘                       │
│           │                                                         │
│           │ Network Packets (playlist sync, play audio URL)         │
│           ▼                                                         │
│  ┌─────────────────┐    ┌──────────────────┐                       │
│  │  Mineify Mod    │───►│  AudioPlayer     │                       │
│  │  (Client-side)  │    │  (WAV via HTTP)  │                       │
│  └─────────────────┘    └──────────────────┘                       │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ HTTP API
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     MINEIFY COMPANION SERVICE                       │
│  ┌─────────────────┐    ┌──────────────────┐    ┌────────────────┐ │
│  │  Express Server │◄──►│ youtube-search-  │    │    yt-dlp      │ │
│  │  (REST API)     │    │ api (npm)        │    │  (Downloader)  │ │
│  └─────────────────┘    └──────────────────┘    └────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

### How It Works

1. **Fabric Mod (Java)**: Handles Minecraft integration - GUI, keybindings, networking, and audio playback
2. **Companion Service (Node.js)**: Handles YouTube search and download using free tools:
   - `youtube-search-api`: Scrapes YouTube search results without requiring an API key
   - `yt-dlp`: Downloads and converts YouTube audio to WAV format (free, open-source)
3. **Audio Playback**: When a song is added to the playlist, the server asks the companion service to download it, then sends the download URL to all clients. Each client fetches the WAV file over HTTP and plays it locally using `javax.sound.sampled`.

### Features

- **N Keybinding**: Open the Mineify GUI from anywhere in-game
- **YouTube Search**: Search for songs directly in Minecraft
- **Shared Playlist**: All players see and contribute to the same queue
- **Automatic Playback**: Songs play automatically when added and advance through the queue
- **Late-Join Support**: Players who join mid-song receive the current track
- **Session Storage**: Downloads are cached per server session

## Project Structure

```
mine-ify/
├── fabric-mod/                 # Minecraft Fabric mod (Java)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/mineify/
│   │   │   │   ├── Mineify.java              # Main mod entry (server)
│   │   │   │   ├── MineifyConfig.java        # Config loader
│   │   │   │   ├── server/
│   │   │   │   │   ├── PlaylistManager.java  # Server playlist & playback
│   │   │   │   │   └── CompanionClient.java  # HTTP client for companion
│   │   │   │   └── network/
│   │   │   │       ├── MineifyPackets.java   # Packet registration
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
│   │           ├── MineifyClient.java        # Client-side entry
│   │           └── client/
│   │               ├── MineifyScreen.java    # Main GUI screen
│   │               ├── MineifyKeybinds.java  # Keybinding handler
│   │               └── audio/
│   │                   └── AudioPlayer.java  # WAV download & playback
│   ├── build.gradle
│   └── gradle.properties
│
├── companion-service/          # Node.js companion service
│   ├── src/
│   │   ├── index.js            # Express server entry
│   │   ├── routes/
│   │   │   ├── search.js       # YouTube search endpoint
│   │   │   └── download.js     # Download & convert endpoint
│   │   └── services/
│   │       ├── youtube.js      # youtube-search-api wrapper
│   │       └── downloader.js   # yt-dlp wrapper
│   ├── package.json
│   └── .env.example
│
└── README.md
```

## Requirements

### For the Fabric Mod
- Minecraft 1.21.11
- Fabric Loader 0.18.x+
- Fabric API
- Java 21

### For the Companion Service
- Node.js 18+
- yt-dlp (installed on system PATH)
- ffmpeg (installed on system PATH, used by yt-dlp for audio conversion)

## Installation

### Prerequisites

Both server and client require:
- Minecraft 1.21.11
- Fabric Loader 0.18.x+
- Fabric API

The server (or whoever hosts the companion service) also needs:
- Node.js 18+
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) installed and on your system PATH
- [ffmpeg](https://ffmpeg.org/) installed and on your system PATH

### Client Setup

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.11 if you haven't already.
2. Download the [Fabric API](https://modrinth.com/mod/fabric-api) mod and place it in your `.minecraft/mods/` folder.
3. Build the mod (or obtain the JAR):
   ```bash
   cd fabric-mod
   ./gradlew build
   ```
4. Copy `fabric-mod/build/libs/mineify-1.0.0.jar` into your `.minecraft/mods/` folder.
5. Launch Minecraft using the Fabric profile. No additional client configuration is needed — the mod connects to the server automatically.

**Note:** Clients must be able to reach the companion service URL over HTTP to download audio. If the companion service is not on the same machine as the Minecraft server, ensure clients can access it (e.g. by port-forwarding or using a public IP).

### Server Setup

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) on your server for Minecraft 1.21.11.
2. Place both the **Fabric API** JAR and the **Mineify** JAR (`mineify-1.0.0.jar`) into the server's `mods/` folder.
3. Start the companion service:
   ```bash
   cd companion-service
   npm install
   npm start
   ```
   This runs on `http://localhost:3001` by default.
4. Create `config/mineify.json` in your server directory:
   ```json
   {
     "companionServiceUrl": "http://localhost:3001",
     "maxPlaylistSize": 50,
     "audioSessionFolder": "./mineify-sessions"
   }
   ```
   If the companion service runs on a different machine, update `companionServiceUrl` accordingly.
5. Start the Minecraft server. The mod will connect to the companion service on startup.

**Note:** The companion service must be running before players try to search or add songs. The Minecraft server and companion service can run on the same machine or on separate machines — just make sure the URL is reachable from both the server and all clients.

## Usage

1. Join a server with Mineify installed
2. Press **N** to open the Mineify GUI
3. Search for a song using the search bar
4. Click a result to add it to the playlist
5. Songs play automatically for all connected players

## Development

### Building the Mod

Requires Java 21. If your default Java version differs, set `JAVA_HOME`:
```bash
export JAVA_HOME=/path/to/jdk-21
```

```bash
cd fabric-mod
./gradlew build          # Build the mod
./gradlew runClient      # Run test client
./gradlew runServer      # Run test server
```

### Running the Companion Service in Dev Mode
```bash
cd companion-service
npm run dev              # Runs with nodemon for hot reload
```

## Legal Notes

This mod is for personal/educational use. Users are responsible for ensuring they comply with YouTube's Terms of Service and applicable copyright laws. The developers do not endorse or encourage piracy.

## License

MIT License - See LICENSE file for details.
