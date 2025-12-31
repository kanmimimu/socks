package com.aqualantic.socks.platform;

import com.aqualantic.socks.eaglercraft.internal.IWebSocketClient;
import com.aqualantic.socks.websocket.JavaWebSocketClient;

/**
 * Platform layer for WebSocket client creation
 */
public class PlatformNetworking {

    /**
     * Open WebSocket client
     * 
     * @param socketURI WebSocket URI (ws:// or wss://)
     * @return WebSocket client instance
     */
    public static IWebSocketClient openWebSocket(String socketURI) {
        if (!socketURI.startsWith("ws://") && !socketURI.startsWith("wss://")) {
            throw new IllegalArgumentException("Invalid WebSocket URI: " + socketURI);
        }
        return new JavaWebSocketClient(socketURI);
    }

    /**
     * Open WebSocket client without safety checks
     * 
     * @param socketURI WebSocket URI
     * @return WebSocket client instance
     */
    public static IWebSocketClient openWebSocketUnsafe(String socketURI) {
        return new JavaWebSocketClient(socketURI);
    }
}
