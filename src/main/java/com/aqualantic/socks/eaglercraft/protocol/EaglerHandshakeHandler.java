package com.aqualantic.socks.eaglercraft.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;

/**
 * EaglerCraft Handshake Handler
 * Implements the EaglerCraft WebSocket handshake protocol (version 4)
 */
public class EaglerHandshakeHandler {

    private static final Logger logger = LogManager.getLogger("EaglerHandshake");
    private static final int PROTOCOL_VERSION = 4;

    private int state = HandshakePacketTypes.STATE_NEW;

    public int getState() {
        return state;
    }

    /**
     * Create CLIENT_VERSION packet
     * This is the first packet sent to the server
     * Format matches HandshakerHandler.beginHandshake()
     */
    public byte[] createClientVersionPacket(String username) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());

        // Packet ID
        buffer.writeByte(HandshakePacketTypes.PROTOCOL_CLIENT_VERSION);

        // Legacy protocol version
        buffer.writeByte(2);

        // Supported eagler protocol versions
        buffer.writeShort(3); // count
        buffer.writeShort(3); // v3
        buffer.writeShort(4); // v4
        buffer.writeShort(5); // v5

        // Supported game protocols
        buffer.writeShort(1); // count
        buffer.writeShort(47); // Minecraft 1.8

        // Client brand
        String clientBrand = "EaglercraftX";
        buffer.writeByte(clientBrand.length());
        writeASCII(buffer, clientBrand);

        // Client version
        String clientVersion = "u48";
        buffer.writeByte(clientVersion.length());
        writeASCII(buffer, clientVersion);

        // Has password (false for now)
        buffer.writeBoolean(false);

        // Username (IMPORTANT!)
        buffer.writeByte(username.length());
        writeASCII(buffer, username);

        logger.info(
                "[EaglerHandshake] Sending CLIENT_VERSION: protocols=[v3,v4,v5], game=47, brand={}, version={}, username={}",
                clientBrand, clientVersion, username);
        state = HandshakePacketTypes.STATE_CLIENT_VERSION;
        return bufferToBytes(buffer);
    }

    /**
     * Create CLIENT_REQUEST_LOGIN packet with protocol version adaptation
     */
    public byte[] createClientRequestLoginPacket(String username, String requestedServer, byte[] password) {
        // Adapt to server protocol version
        if (serverProtocolVersion == 5) {
            return createClientRequestLoginV5(username, requestedServer, password);
        } else {
            return createClientRequestLoginV4(username, requestedServer, password);
        }
    }

    /**
     * Create CLIENT_REQUEST_LOGIN packet for protocol v4
     */
    private byte[] createClientRequestLoginV4(String username, String requestedServer, byte[] password) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeByte(HandshakePacketTypes.PROTOCOL_CLIENT_REQUEST_LOGIN);

        // Write username
        buffer.writeByte(username.length());
        writeASCII(buffer, username);

        // Write requested server
        buffer.writeByte(requestedServer.length());
        writeASCII(buffer, requestedServer);

        // Write password
        if (password != null) {
            buffer.writeByte(password.length);
            buffer.writeBytes(password);
        } else {
            buffer.writeByte(0);
        }

        // Write cookies
        buffer.writeBoolean(false);
        buffer.writeByte(0);

        return logAndReturnPacket(buffer, username, requestedServer, "v4");
    }

    /**
     * Create CLIENT_REQUEST_LOGIN packet for protocol v5
     */
    private byte[] createClientRequestLoginV5(String username, String requestedServer, byte[] password) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeByte(HandshakePacketTypes.PROTOCOL_CLIENT_REQUEST_LOGIN);

        // Write username - ALWAYS send username (nicknameSelection only controls if server allows changing it)
        buffer.writeByte(username.length());
        writeASCII(buffer, username);

        // Write requested server
        buffer.writeByte(requestedServer.length());
        writeASCII(buffer, requestedServer);

        // Write password
        if (password != null) {
            buffer.writeByte(password.length);
            buffer.writeBytes(password);
        } else {
            buffer.writeByte(0);
        }

        // Write cookies
        buffer.writeBoolean(false);
        buffer.writeByte(0);

        // v5 NEW: Client Capabilities
        // Minimal implementation: // Client Capabilities
        // REDIRECT(2) | NOTIFICATION(3) | PAUSE_MENU(4)
        // (1<<2) | (1<<3) | (1<<4) = 4 + 8 + 16 = 28
        int standardCaps = 28;
        writeVarInt(buffer, standardCaps);

        // Versions for the 3 capabilities (all version 0)
        writeVarInt(buffer, 0); // REDIRECT ver 0
        writeVarInt(buffer, 0); // NOTIFICATION ver 0
        writeVarInt(buffer, 0); // PAUSE_MENU ver 0

        buffer.writeByte(0); // extendedCapsCount = 0 (no extended capabilities)

        return logAndReturnPacket(buffer, username, requestedServer, "v5");
    }

    private byte[] logAndReturnPacket(PacketBuffer buffer, String username, String requestedServer, String version) {
        byte[] packet = bufferToBytes(buffer);
        StringBuilder hexDump = new StringBuilder();
        for (int i = 0; i < Math.min(packet.length, 50); i++) {
            hexDump.append(String.format("%02X ", packet[i] & 0xFF));
        }
        logger.info(
                "[EaglerHandshake] [PHASE: CLIENT_LOGIN v{}] Sending CLIENT_REQUEST_LOGIN: username={}, server={}, packet_size={}, hex={}",
                version, username, requestedServer.isEmpty() ? "<default>" : requestedServer, packet.length,
                hexDump.toString());
        state = HandshakePacketTypes.STATE_CLIENT_LOGIN;
        return packet;
    }

    /**
     * Create CLIENT_FINISH_LOGIN packet
     * This finalizes the handshake and transitions to the Minecraft protocol
     */
    public byte[] createClientFinishLoginPacket() {
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        buffer.writeByte(HandshakePacketTypes.PROTOCOL_CLIENT_FINISH_LOGIN);
        logger.info("[EaglerHandshake] Sending CLIENT_FINISH_LOGIN");
        state = HandshakePacketTypes.STATE_FINISHED;
        return bufferToBytes(buffer);
    }

    /**
     * Process server response packet
     * Returns true if handshake should continue, false if completed or error
     */
    public boolean processServerPacket(byte[] data) {
        if (data == null || data.length == 0) {
            logger.warn("[EaglerHandshake] Received empty packet");
            return false;
        }

        int packetType = data[0] & 0xFF;
        logger.info("[EaglerHandshake] [PHASE: RECEIVE] Received packet type: 0x{}, size: {} bytes",
                Integer.toHexString(packetType), data.length);

        switch (packetType) {
            case HandshakePacketTypes.PROTOCOL_SERVER_VERSION:
                return handleServerVersion(data);

            case HandshakePacketTypes.PROTOCOL_SERVER_ALLOW_LOGIN:
                return handleServerAllowLogin(data);

            case HandshakePacketTypes.PROTOCOL_SERVER_DENY_LOGIN:
                return handleServerDenyLogin(data);

            case HandshakePacketTypes.PROTOCOL_SERVER_FINISH_LOGIN:
                return handleServerFinishLogin(data);

            case HandshakePacketTypes.PROTOCOL_VERSION_MISMATCH:
                handleVersionMismatch(data);
                return false;

            case HandshakePacketTypes.PROTOCOL_SERVER_ERROR:
                handleServerError(data);
                return false;

            default:
                logger.warn("[EaglerHandshake] Unknown packet type: 0x{}", Integer.toHexString(packetType));
                return false;
        }
    }

    private int serverProtocolVersion = -1;
    private int serverAuthType = -1;
    private byte[] serverAuthSalt = null;
    private boolean nicknameSelection = false;

    public String responseUsername;
    public java.util.UUID responseUUID;
    public String lastError; // Capture error message for GUI display

    private boolean handleServerVersion(byte[] data) {
        try {
            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
            buffer.readByte(); // Skip packet ID

            // Read protocol version
            serverProtocolVersion = buffer.readUnsignedShort();
            logger.info("[EaglerHandshake] [PHASE: SERVER_VERSION] Server protocol version: {}", serverProtocolVersion);

            // Read game version
            int gameVersion = buffer.readUnsignedShort();
            logger.info("[EaglerHandshake] [PHASE: SERVER_VERSION] Game version: {} (expected: 47)", gameVersion);

            // Read plugin brand
            int brandLen = buffer.readUnsignedByte();
            String pluginBrand = readString(buffer, brandLen);
            logger.info("[EaglerHandshake] [PHASE: SERVER_VERSION] Plugin brand: {}", pluginBrand);

            // Read plugin version
            int versionLen = buffer.readUnsignedByte();
            String pluginVersion = readString(buffer, versionLen);
            logger.info("[EaglerHandshake] [PHASE: SERVER_VERSION] Plugin version: {}", pluginVersion);

            // Read auth type
            serverAuthType = buffer.readUnsignedByte();
            logger.info("[EaglerHandshake] [PHASE: SERVER_VERSION] Auth type: 0x{}",
                    Integer.toHexString(serverAuthType));

            // Read auth salt
            int saltLen = buffer.readUnsignedShort();
            serverAuthSalt = new byte[saltLen];
            buffer.readBytes(serverAuthSalt);
            logger.info("[EaglerHandshake] [PHASE: SERVER_VERSION] Auth salt length: {}", saltLen);

            // v5+: Read nicknameSelection flag
            if (serverProtocolVersion >= 5) {
                nicknameSelection = buffer.readBoolean();
                logger.info("[EaglerHandshake] [PHASE: SERVER_VERSION] Nickname selection: {}", nicknameSelection);
            }

            logger.info("[EaglerHandshake] Server accepted protocol version v{}", serverProtocolVersion);
            state = HandshakePacketTypes.STATE_CLIENT_VERSION;
            return true;
        } catch (Exception e) {
            logger.error("[EaglerHandshake] Failed to parse SERVER_VERSION", e);
            return false;
        }
    }

    private boolean handleServerAllowLogin(byte[] data) {
        logger.info("[EaglerHandshake] Server allowed login");

        try {
            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
            buffer.readByte(); // packet ID

            // Read username
            int nameLen = buffer.readUnsignedByte();
            responseUsername = readASCII(buffer, nameLen);

            // Read UUID
            long msb = buffer.readLong();
            long lsb = buffer.readLong();
            responseUUID = new java.util.UUID(msb, lsb);

            logger.info("[EaglerHandshake] [PHASE: SERVER_ALLOW_LOGIN] Username: {}, UUID: {}", responseUsername,
                    responseUUID);
        } catch (Exception e) {
            logger.error("[EaglerHandshake] Failed to parse SERVER_ALLOW_LOGIN", e);
        }

        state = HandshakePacketTypes.STATE_CLIENT_COMPLETE;
        return true;
    }

    private boolean handleServerDenyLogin(byte[] data) {
        if (data.length > 1) {
            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data, 1, data.length - 1));
            int reasonLen = buffer.readUnsignedByte();
            String reason = readString(buffer, reasonLen);
            logger.error("[EaglerHandshake] Server denied login: {}", reason);
            this.lastError = reason;
        } else {
            logger.error("[EaglerHandshake] Server denied login");
            this.lastError = "Login denied by server";
        }
        return false;
    }

    private boolean handleServerFinishLogin(byte[] data) {
        logger.info("[EaglerHandshake] Server finished login - handshake complete!");
        state = HandshakePacketTypes.STATE_FINISHED;
        return false; // Handshake complete
    }

    private void handleVersionMismatch(byte[] data) {
        logger.error("[EaglerHandshake] Protocol version mismatch!");
    }

    private void handleServerError(byte[] data) {
        if (data.length > 2) {
            int errorCode = data[1] & 0xFF;
            String errorCodeName = getErrorCodeName(errorCode);
            logger.error("[EaglerHandshake] Server error: {} (0x{})", errorCodeName, Integer.toHexString(errorCode));

            // If custom message (0x08), read the message
            if (errorCode == 0x08 && data.length > 4) {
                try {
                    PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
                    buffer.readByte(); // skip packet ID
                    buffer.readByte(); // skip error code
                    int msgLen = buffer.readUnsignedShort();
                    if (msgLen > 0 && buffer.readableBytes() >= msgLen) {
                        String errorMessage = readString(buffer, msgLen);
                        logger.error("[EaglerHandshake] Server message: {}", errorMessage);
                        this.lastError = errorMessage;
                    }
                } catch (Exception e) {
                    logger.error("[EaglerHandshake] Failed to read error message", e);
                }
            } else {
                 this.lastError = "Server Error: " + errorCodeName;
            }
        } else {
            logger.error("[EaglerHandshake] Server error (unknown)");
            this.lastError = "Unknown Server Error";
        }
    }

    private String getErrorCodeName(int code) {
        switch (code) {
            case 0x01:
                return "UNKNOWN_PACKET";
            case 0x02:
                return "INVALID_PACKET";
            case 0x03:
                return "WRONG_PACKET";
            case 0x04:
                return "EXCESSIVE_PROFILE_DATA";
            case 0x05:
                return "DUPLICATE_PROFILE_DATA";
            case 0x06:
                return "RATELIMIT_BLOCKED";
            case 0x07:
                return "RATELIMIT_LOCKED";
            case 0x08:
                return "CUSTOM_MESSAGE";
            case 0x09:
                return "AUTHENTICATION_REQUIRED";
            default:
                return "UNKNOWN(" + code + ")";
        }
    }

    // Utility methods

    private static void writeASCII(PacketBuffer buffer, String str) {
        // Match HandshakerHandler implementation exactly
        for (int i = 0, l = str.length(); i < l; ++i) {
            buffer.writeByte(str.charAt(i));
        }
    }

    private static String readString(PacketBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String readASCII(PacketBuffer buffer, int length) {
        return readString(buffer, length);
    }

    private static byte[] bufferToBytes(PacketBuffer buffer) {
        // Use PacketBuffer.toBytes() method to get only the written bytes
        // buffer.copy() would include reader/writer indices which can cause issues
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(0, bytes);
        return bytes;
    }

    // VarInt encoding/decoding utilities (Minecraft protocol)
    private static void writeVarInt(PacketBuffer buffer, int value) {
        while ((value & ~0x7F) != 0) {
            buffer.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buffer.writeByte(value);
    }

    private static int readVarInt(PacketBuffer buffer) {
        int value = 0;
        int position = 0;
        byte currentByte;
        while (true) {
            currentByte = buffer.readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0)
                break;
            position += 7;
            if (position >= 32)
                throw new RuntimeException("VarInt too big");
        }
        return value;
    }
}