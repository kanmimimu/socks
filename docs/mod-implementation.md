# Socks Mod Implementation

This document describes how the Socks mod enables vanilla Minecraft to connect to EaglerCraft WebSocket servers.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Minecraft 1.8.9                             │
├─────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐   ┌──────────────────┐   ┌──────────────────────┐ │
│  │ GuiConnecting│──>│ ConnectionHandler│──>│ WebSocketNetworkMgr  │ │
│  │ (ASM Hooked) │   │    (Entry Point) │   │     (WebSocket I/O)  │ │
│  └──────────────┘   └──────────────────┘   └──────────────────────┘ │
│                              │                        │             │
│                              v                        v             │
│                     ┌────────────────┐      ┌─────────────────────┐ │
│                     │ EaglerHandshake│      │ JavaWebSocketClient │ │
│                     │    Handler     │      │ (java-websocket lib)│ │
│                     └────────────────┘      └─────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. ASM Class Transformer (`SocksClassTransformer`)

**Location:** `com.aqualantic.socks.asm.SocksClassTransformer`

Hooks into Minecraft classes at load time:

| Class | Transformation |
|-------|---------------|
| `GuiConnecting` | Intercept server connection creation |
| `GuiMultiplayer` | Replace `OldServerPinger` with `CustomServerPinger` |
| `FMLClientHandler` | Inject WebSocket icon rendering |

### 2. Connection Handler (`ConnectionHandler`)

**Location:** `com.aqualantic.socks.network.ConnectionHandler`

Entry point for WebSocket connections:

```java
public static void connectToEaglerServer(
    String serverAddress,    // ws:// or wss:// URL
    String username          // Player username
)
```

**Flow:**
1. Parse WebSocket URL
2. Create `EaglerHandshakeHandler`
3. Open WebSocket connection
4. Perform EaglerCraft handshake
5. Transition to `WebSocketNetworkManager`

### 3. WebSocket Network Manager

**Location:** `com.aqualantic.socks.eaglercraft.socket.WebSocketNetworkManager`

Replaces Minecraft's TCP-based `NetworkManager` with WebSocket:

- Implements same interface as vanilla `NetworkManager`
- Wraps `Java-WebSocket` library for WebSocket I/O
- Converts WebSocket binary frames to Minecraft packets

### 4. Handshake Handler (`EaglerHandshakeHandler`)

**Location:** `com.aqualantic.socks.eaglercraft.protocol.EaglerHandshakeHandler`

Implements EaglerCraft handshake protocol:

```java
// Create and send packets
byte[] createClientVersionPacket(String username)
byte[] createClientRequestLoginPacket(String username, String server, byte[] password)
byte[] createClientFinishLoginPacket()

// Process server responses
boolean processServerPacket(byte[] data)
```

### 5. Custom Server Pinger (`CustomServerPinger`)

**Location:** `com.aqualantic.socks.network.CustomServerPinger`

Enables server list to display EaglerCraft servers:

- Detects `ws://` and `wss://` addresses
- Uses `EaglerServerQuery` for MOTD queries
- Parses JSON response for player count, MOTD, icon

### 6. WebSocket Icon Renderer (`WebSocketIconRenderer`)

**Location:** `com.aqualantic.socks.hooks.WebSocketIconRenderer`

Displays WebSocket icon in server list:

- ASM-injected into `FMLClientHandler.enhanceServerListEntry`
- Uses reflection for obfuscation compatibility
- Shows tooltip "WebSocket (EaglerCraft) Connection"

## Connection Flow

### 1. User Adds WebSocket Server

```
User enters: wss://example.eaglercraft.com/
```

### 2. Server List Ping

```
CustomServerPinger.ping()
    ↓
EaglerServerQuery (WebSocket binary query)
    ↓
JSON Response: { name, motd, online, max, icon }
    ↓
Display in ServerListEntryNormal with WebSocket icon
```

### 3. User Clicks "Join Server"

```
GuiConnecting (ASM hooked)
    ↓
GuiConnectingHook.checkAndRedirectWebSocket()
    ↓
ConnectionHandler.connectToEaglerServer()
    ↓
WebSocket Connect
    ↓
EaglerHandshakeHandler
    ↓
WebSocketNetworkManager (game packets)
```

## Obfuscation Handling

The mod supports both development (MCP names) and production (SRG names):

```java
// Example: Accessing ServerData.serverIP
Field serverIPField;
try {
    serverIPField = ServerData.class.getDeclaredField("serverIP");  // MCP
} catch (NoSuchFieldException e) {
    serverIPField = ServerData.class.getDeclaredField("field_78845_b");  // SRG
}
```

**Key Field Mappings:**
| MCP Name | SRG Name | Class |
|----------|----------|-------|
| serverIP | field_78845_b | ServerData |
| owner | field_148303_c | ServerListEntryNormal |
| networkManager | field_146371_g | GuiConnecting |

## ASM Injection Points

### GuiConnecting.<init>

**Purpose:** Intercept new connection creation

```java
// Before: Creates TCP NetworkManager
// After: Checks for ws:// and redirects to WebSocket handler
```

### FMLClientHandler.enhanceServerListEntry

**Purpose:** Draw WebSocket icon for EaglerCraft servers

```java
// Injected at method start:
WebSocketIconRenderer.renderIconInDrawEntry(
    serverListEntry,  // For tooltip access
    serverData,       // Check if ws:// address
    x, width, y,      // Coordinates
    mouseX, mouseY    // For hover detection
);
```

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Java-WebSocket | 1.5.2+ | WebSocket client |
| Netty | (bundled) | Packet buffering |
| ASM | 5.0.3 | Bytecode manipulation |

## Error Handling

### Connection Errors

```java
// WebSocket connection failed
catch (Exception e) {
    mc.displayGuiScreen(new GuiDisconnected(
        previousScreen,
        "connect.failed",
        new ChatComponentText("WebSocket connection failed: " + e.getMessage())
    ));
}
```

### Handshake Errors

- `SERVER_DENY_LOGIN`: Display server's denial message
- `VERSION_MISMATCH`: Protocol version incompatible
- `SERVER_ERROR`: Display error code and message

## File Structure

```
src/main/java/com/aqualantic/socks/
├── asm/
│   ├── SocksCoremod.java          # FML coremod entry
│   ├── SocksClassTransformer.java # ASM transformations
│   └── GuiConnectingHook.java     # Connection hook
├── eaglercraft/
│   ├── protocol/
│   │   ├── EaglerHandshakeHandler.java
│   │   └── HandshakePacketTypes.java
│   └── socket/
│       └── WebSocketNetworkManager.java
├── hooks/
│   ├── WebSocketIconRenderer.java
│   └── WebSocketIconOverlay.java
├── network/
│   ├── ConnectionHandler.java
│   ├── CustomServerPinger.java
│   └── EaglerServerQuery.java
└── websocket/
    └── JavaWebSocketClient.java
```
