package com.aqualantic.socks.network;

import com.aqualantic.socks.eaglercraft.internal.IWebSocketClient;
import com.aqualantic.socks.eaglercraft.socket.WebSocketNetworkManager;
import com.aqualantic.socks.platform.PlatformNetworking;
import net.minecraft.network.NetworkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Connection handler for server connections
 * Determines between WebSocket and TCP connections and creates appropriate
 * NetworkManager
 */
public class ConnectionHandler {

    private static final Logger logger = LogManager.getLogger("Socks");

    /**
     * Determine if server address is a WebSocket connection
     * 
     * @param address Server address
     * @return true if WebSocket
     */
    public static boolean isWebSocketAddress(String address) {
        return address != null && (address.startsWith("ws://") || address.startsWith("wss://"));
    }

    /**
     * Create NetworkManager for WebSocket
     * 
     * @param address WebSocket server URI
     * @return WebSocketNetworkManager
     */
    public static WebSocketNetworkManager createWebSocketNetworkManager(String address) {
        logger.info("Creating WebSocket connection to: {}", address);

        try {
            IWebSocketClient client = PlatformNetworking.openWebSocket(address);

            // Try to connect with 5 second timeout
            boolean connected = client.connectBlocking(5000);

            if (!connected) {
                logger.warn("Failed to connect to WebSocket server: {}", address);
                throw new RuntimeException("WebSocket connection timeout");
            }

            logger.info("WebSocket connected successfully");
            return new WebSocketNetworkManager(client);

        } catch (Exception e) {
            logger.error("Failed to create WebSocket connection: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create WebSocket connection", e);
        }
    }

    /**
     * Remove port number from address string
     * ws://example.com:25565/ -> ws://example.com/
     * 
     * @param address Original address
     * @return Address after port removal
     */
    public static String normalizeWebSocketAddress(String address) {
        if (!isWebSocketAddress(address)) {
            return address;
        }

        // Normalize WebSocket address
        // ws://host:port/path -> ws://host/path format
        try {
            java.net.URI uri = new java.net.URI(address);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();

            if (path == null || path.isEmpty()) {
                path = "/";
            }

            // Include port if specified
            if (port != -1) {
                return scheme + "://" + host + ":" + port + path;
            } else {
                return scheme + "://" + host + path;
            }
        } catch (Exception e) {
            logger.warn("Failed to normalize WebSocket address: {}", address, e);
            return address;
        }
    }
}
