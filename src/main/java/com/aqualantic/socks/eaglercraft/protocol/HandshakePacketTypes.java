package com.aqualantic.socks.eaglercraft.protocol;

/**
 * EaglerCraft Handshake Packet Types
 * Ported from EaglerCraftX 1.8
 */
public class HandshakePacketTypes {

    // Protocol packet types
    public static final int PROTOCOL_CLIENT_VERSION = 0x01;
    public static final int PROTOCOL_SERVER_VERSION = 0x02;
    public static final int PROTOCOL_VERSION_MISMATCH = 0x03;
    public static final int PROTOCOL_CLIENT_REQUEST_LOGIN = 0x04;
    public static final int PROTOCOL_SERVER_ALLOW_LOGIN = 0x05;
    public static final int PROTOCOL_SERVER_DENY_LOGIN = 0x06;
    public static final int PROTOCOL_CLIENT_PROFILE_DATA = 0x07;
    public static final int PROTOCOL_CLIENT_FINISH_LOGIN = 0x08;
    public static final int PROTOCOL_SERVER_FINISH_LOGIN = 0x09;
    public static final int PROTOCOL_SERVER_REDIRECT_TO = 0x0A;
    public static final int PROTOCOL_SERVER_ERROR = 0xFF;

    // Handshake states
    public static final int STATE_NEW = 0x00;
    public static final int STATE_OPENED = 0x01;
    public static final int STATE_CLIENT_VERSION = 0x02;
    public static final int STATE_CLIENT_LOGIN = 0x03;
    public static final int STATE_CLIENT_COMPLETE = 0x04;
    public static final int STATE_FINISHED = 0x05;

    // Server error codes
    public static final int SERVER_ERROR_UNKNOWN_PACKET = 0x01;
    public static final int SERVER_ERROR_INVALID_PACKET = 0x02;
    public static final int SERVER_ERROR_WRONG_PACKET = 0x03;
    public static final int SERVER_ERROR_EXCESSIVE_PROFILE_DATA = 0x04;
    public static final int SERVER_ERROR_DUPLICATE_PROFILE_DATA = 0x05;
    public static final int SERVER_ERROR_RATELIMIT_BLOCKED = 0x06;
    public static final int SERVER_ERROR_RATELIMIT_LOCKED = 0x07;
    public static final int SERVER_ERROR_CUSTOM_MESSAGE = 0x08;
    public static final int SERVER_ERROR_AUTHENTICATION_REQUIRED = 0x09;

    // Authentication methods
    public static final int AUTH_METHOD_NONE = 0x0;
    public static final int AUTH_METHOD_EAGLER_SHA256 = 0x01;
    public static final int AUTH_METHOD_AUTHME_SHA256 = 0x02;
    public static final int AUTH_METHOD_PLAINTEXT = 0xFF;
}
