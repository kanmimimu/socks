# EaglerCraft WebSocket Protocol Specification

This document describes how EaglerCraft clients and servers communicate over WebSocket.

## Overview

EaglerCraft uses a custom WebSocket-based protocol to enable Minecraft gameplay in web browsers. The protocol consists of two phases:

1. **Handshake Phase** - Custom EaglerCraft protocol for authentication and version negotiation
2. **Play Phase** - Standard Minecraft 1.8 protocol over WebSocket binary frames

## Connection Flow

```
┌──────────┐                     ┌──────────┐
│  Client  │                     │  Server  │
└────┬─────┘                     └────┬─────┘
     │                                │
     │   WebSocket Connect            │
     │───────────────────────────────>│
     │                                │
     │   CLIENT_VERSION (0x01)        │
     │───────────────────────────────>│
     │                                │
     │   SERVER_VERSION (0x02)        │
     │<───────────────────────────────│
     │                                │
     │   CLIENT_REQUEST_LOGIN (0x04)  │
     │───────────────────────────────>│
     │                                │
     │   SERVER_ALLOW_LOGIN (0x05)    │
     │<───────────────────────────────│
     │                                │
     │   CLIENT_PROFILE_DATA (0x07)   │
     │───────────────────────────────>│
     │                                │
     │   CLIENT_FINISH_LOGIN (0x08)   │
     │───────────────────────────────>│
     │                                │
     │   SERVER_FINISH_LOGIN (0x09)   │
     │<───────────────────────────────│
     │                                │
     ├────── Minecraft Protocol ──────┤
     │                                │
```

## Handshake Packet Types

| ID | Name | Direction | Description |
|----|------|-----------|-------------|
| `0x01` | CLIENT_VERSION | C→S | Client sends supported protocol versions |
| `0x02` | SERVER_VERSION | S→C | Server responds with selected protocol |
| `0x03` | VERSION_MISMATCH | S→C | No compatible protocol version found |
| `0x04` | CLIENT_REQUEST_LOGIN | C→S | Client requests to login |
| `0x05` | SERVER_ALLOW_LOGIN | S→C | Server allows login, sends username/UUID |
| `0x06` | SERVER_DENY_LOGIN | S→C | Server denies login with reason |
| `0x07` | CLIENT_PROFILE_DATA | C→S | Client sends skin/cape data |
| `0x08` | CLIENT_FINISH_LOGIN | C→S | Client signals ready for game |
| `0x09` | SERVER_FINISH_LOGIN | S→C | Server confirms, switches to Minecraft protocol |
| `0x0A` | SERVER_REDIRECT_TO | S→C | Redirect client to another server |
| `0xFF` | SERVER_ERROR | S→C | Server error with error code |

## Packet Structures

### CLIENT_VERSION (0x01)

```
┌─────────────────────────────────────────────────────────────┐
│ Byte │ Field                                                │
├──────┼──────────────────────────────────────────────────────┤
│ 0    │ Packet ID (0x01)                                     │
│ 1    │ Legacy protocol version (2)                          │
│ 2-3  │ Supported protocol count (Short)                     │
│ 4+   │ Supported protocol versions (Short[])                │
│ +2   │ Supported game protocol count (Short)                │
│ +    │ Game protocol versions (Short[]) - typically [47]    │
│ +1   │ Client brand length (Byte)                           │
│ +    │ Client brand ASCII string                            │
│ +1   │ Client version length (Byte)                         │
│ +    │ Client version ASCII string                          │
│ +1   │ Has password (Boolean)                               │
│ +1   │ Username length (Byte)                               │
│ +    │ Username ASCII string                                │
└─────────────────────────────────────────────────────────────┘
```

**Example:**
```
01                      ; Packet ID
02                      ; Legacy version
00 03                   ; 3 supported protocols
00 03 00 04 00 05       ; v3, v4, v5
00 01                   ; 1 game protocol
00 2F                   ; Minecraft 1.8 (47)
0B                      ; Brand length: 11
45 61 67 6C 65 72 ...   ; "EaglercraftX"
03                      ; Version length: 3
75 34 38                ; "u48"
00                      ; No password
05                      ; Username length: 5
53 74 65 76 65          ; "Steve"
```

### SERVER_VERSION (0x02)

```
┌─────────────────────────────────────────────────────────────┐
│ Byte │ Field                                                │
├──────┼──────────────────────────────────────────────────────┤
│ 0    │ Packet ID (0x02)                                     │
│ 1-2  │ Selected protocol version (Short)                    │
│ 3-4  │ Game version (Short) - typically 47                  │
│ 5    │ Plugin brand length (Byte)                           │
│ +    │ Plugin brand string                                  │
│ +1   │ Plugin version length (Byte)                         │
│ +    │ Plugin version string                                │
│ +1   │ Auth type (Byte) - see Auth Methods                  │
│ +2   │ Auth salt length (Short)                             │
│ +    │ Auth salt bytes                                      │
│ +1   │ [v5+] Nickname selection allowed (Boolean)           │
└─────────────────────────────────────────────────────────────┘
```

### CLIENT_REQUEST_LOGIN (0x04)

#### Protocol v4
```
┌─────────────────────────────────────────────────────────────┐
│ Byte │ Field                                                │
├──────┼──────────────────────────────────────────────────────┤
│ 0    │ Packet ID (0x04)                                     │
│ 1    │ Username length (Byte)                               │
│ +    │ Username ASCII string                                │
│ +1   │ Requested server length (Byte)                       │
│ +    │ Requested server ASCII string                        │
│ +1   │ Password length (Byte)                               │
│ +    │ Password bytes                                       │
│ +1   │ Cookies enabled (Boolean)                            │
│ +1   │ Cookie data length (Byte)                            │
│ +    │ Cookie data (if any)                                 │
└─────────────────────────────────────────────────────────────┘
```

#### Protocol v5 (Additional Fields)
```
┌─────────────────────────────────────────────────────────────┐
│ After v4 fields...                                          │
├─────────────────────────────────────────────────────────────┤
│ +VarInt │ Standard capabilities bitmask                     │
│ +VarInt │ Capability versions (one per enabled cap)         │
│ +1      │ Extended capabilities count (Byte)                │
│ +       │ Extended capability data                          │
└─────────────────────────────────────────────────────────────┘
```

**Capability Flags (Standard):**
| Bit | Capability |
|-----|------------|
| 2 | REDIRECT |
| 3 | NOTIFICATION |
| 4 | PAUSE_MENU |

### SERVER_ALLOW_LOGIN (0x05)

```
┌─────────────────────────────────────────────────────────────┐
│ Byte │ Field                                                │
├──────┼──────────────────────────────────────────────────────┤
│ 0    │ Packet ID (0x05)                                     │
│ 1    │ Username length (Byte)                               │
│ +    │ Assigned username ASCII string                       │
│ +8   │ UUID most significant bits (Long)                    │
│ +8   │ UUID least significant bits (Long)                   │
└─────────────────────────────────────────────────────────────┘
```

### SERVER_ERROR (0xFF)

```
┌─────────────────────────────────────────────────────────────┐
│ Byte │ Field                                                │
├──────┼──────────────────────────────────────────────────────┤
│ 0    │ Packet ID (0xFF)                                     │
│ 1    │ Error code (Byte) - see Error Codes                  │
│ 2-3  │ [If CUSTOM_MESSAGE] Message length (Short)           │
│ +    │ [If CUSTOM_MESSAGE] Error message string             │
└─────────────────────────────────────────────────────────────┘
```

**Error Codes:**
| Code | Name | Description |
|------|------|-------------|
| 0x01 | UNKNOWN_PACKET | Unknown packet type received |
| 0x02 | INVALID_PACKET | Malformed packet |
| 0x03 | WRONG_PACKET | Unexpected packet for current state |
| 0x04 | EXCESSIVE_PROFILE_DATA | Profile data too large |
| 0x05 | DUPLICATE_PROFILE_DATA | Profile already sent |
| 0x06 | RATELIMIT_BLOCKED | Rate limited |
| 0x07 | RATELIMIT_LOCKED | Rate limit lockout |
| 0x08 | CUSTOM_MESSAGE | Custom error with message |
| 0x09 | AUTHENTICATION_REQUIRED | Auth required but not provided |

## Authentication Methods

| Code | Method | Description |
|------|--------|-------------|
| 0x00 | NONE | No authentication required |
| 0x01 | EAGLER_SHA256 | EaglerCraft SHA256 password hash |
| 0x02 | AUTHME_SHA256 | AuthMe compatible hash |
| 0xFF | PLAINTEXT | Plaintext password (insecure) |

## Handshake States

```
STATE_NEW (0x00)
    ↓ WebSocket opened
STATE_OPENED (0x01)
    ↓ CLIENT_VERSION sent
STATE_CLIENT_VERSION (0x02)
    ↓ SERVER_VERSION received, CLIENT_REQUEST_LOGIN sent
STATE_CLIENT_LOGIN (0x03)
    ↓ SERVER_ALLOW_LOGIN received, CLIENT_FINISH_LOGIN sent
STATE_CLIENT_COMPLETE (0x04)
    ↓ SERVER_FINISH_LOGIN received
STATE_FINISHED (0x05)
    → Switch to Minecraft protocol
```

## Post-Handshake: Minecraft Protocol

After `SERVER_FINISH_LOGIN`, the connection switches to standard Minecraft 1.8 protocol:

1. All packets are sent as WebSocket **binary frames**
2. Packet format: `[VarInt Length][VarInt PacketID][Payload]`
3. No compression (typically disabled by server for EaglerCraft)
4. First server packet: Login Success (0x02)
5. First client packet: Keep-Alive responses

## Server Query Protocol (MOTD)

For server list pings, EaglerCraft uses a separate query protocol:

### Query Request
```
┌─────────────────────────────────────────────────────────────┐
│ First byte indicates query type:                            │
│ - 0x00: Server info query (MOTD)                            │
└─────────────────────────────────────────────────────────────┘
```

### Query Response (JSON)
```json
{
  "name": "Server Name",
  "motd": ["Line 1", "Line 2"],
  "online": 100,
  "max": 200,
  "players": ["Player1", "Player2"],
  "icon": true
}
```

### Icon Response (Binary)
If `icon: true`, server sends a 4096-byte PNG icon (64x64 pixels).

## References

- [EaglerCraftX Source](https://gitlab.com/AltoriaDev/EaglercraftX-1.8)
- [EaglerXServer Source](https://github.com/AltoriaDev/eaglerxserver)
- [Minecraft Protocol (1.8)](https://wiki.vg/Protocol)
