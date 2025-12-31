# Socks

> [!IMPORTANT]
> This is all made by AI except for "WebSocket" icon and Socks branding icon.
> Used AI: Claude 4.5 Opus/Sonnet, Gemini 3 Pro

![Socks Banner](images/branding/socks-large.png)

**Minecraft 1.8.9 Forge Mod** - Connect to EaglerCraft WebSocket servers (`ws://` and `wss://`) from the vanilla Minecraft client.

## Features

- 🌐 **WebSocket Connection Support** - Connect to EaglerCraft servers using `ws://` or `wss://` addresses
- 📡 **Server List Integration** - WebSocket servers appear in your multiplayer server list with full MOTD, player count, and ping support
- 🔌 **Custom Icon** - WebSocket servers display a unique icon in the server list
- 🔒 **Secure Connections** - Full support for `wss://` (WebSocket Secure) connections

## Installation

1. Download the latest `socks-x.x.jar` from [Releases](../../releases)
2. Place the JAR file in your Minecraft `mods` folder
3. Launch Minecraft with Forge 1.8.9

## Usage

1. Open **Multiplayer** in Minecraft
2. Click **Add Server**
3. Enter a WebSocket server address:
   - `wss://example.server.com/` (secure)
   - `ws://example.server.com/` (insecure)
4. Click **Done** and connect!

## Requirements

- Minecraft 1.8.9
- Forge 11.15.1.2318 or compatible

## Building from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/socks.git
cd socks

# Build the mod
./gradlew reobfJar

# Output: build/libs/socks-x.x.jar
```

## Technical Details

- Uses ASM for bytecode manipulation to hook into Minecraft's networking
- Implements EaglerCraft handshake protocol for server compatibility
- Custom `NetworkManager` wrapper for WebSocket communication

## License

MIT License - See [LICENSE](LICENSE) for details.

## Credits

- [EaglerCraft](https://eaglercraft.com/) - Protocol reference
- Minecraft Forge - Modding framework
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) - WebSocket library
