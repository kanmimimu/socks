package com.aqualantic.socks.hooks;

import com.aqualantic.socks.network.ConnectionHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;

/**
 * Connection hooks that intercept Minecraft's network connection creation
 */
public class ConnectionHooks {

    private static final Logger logger = LogManager.getLogger("SocksHooks");

    /**
     * Replacement for NetworkManager.createNetworkManagerAndConnect
     * This method checks if the address is a WebSocket URL and creates the
     * appropriate connection
     */
    public static NetworkManager createNetworkManagerAndConnect(InetAddress address, int serverPort,
            boolean useNativeTransport) {
        // Try to get the original server address string that we stored
        String originalAddress = ServerAddressStorage.getAndClearServerAddress();

        if (originalAddress != null) {
            logger.info("createNetworkManagerAndConnect called with stored address: {}", originalAddress);

            // Check if this is a WebSocket address
            if (ConnectionHandler.isWebSocketAddress(originalAddress)) {
                logger.info("Detected WebSocket address, creating WebSocket connection");

                try {
                    // Create WebSocket connection
                    String wsAddress = ConnectionHandler.normalizeWebSocketAddress(originalAddress);
                    com.aqualantic.socks.eaglercraft.socket.WebSocketNetworkManager wsManager = ConnectionHandler
                            .createWebSocketNetworkManager(wsAddress);

                    // Wrap it in a NetworkManager-compatible wrapper
                    return new WebSocketNetworkManagerWrapper(wsManager);

                } catch (Exception e) {
                    logger.error("Failed to create WebSocket connection", e);
                    // Fall back to showing an error
                    throw new RuntimeException("Failed to connect to WebSocket server: " + e.getMessage());
                }
            }
        }

        // Regular TCP connection - use original method
        logger.info("createNetworkManagerAndConnect fallback to TCP: {}:{}", address, serverPort);
        return NetworkManager.func_181124_a(address, serverPort, useNativeTransport);
    }

    /**
     * Alternative hook point that receives the server address as a string
     */
    public static NetworkManager createNetworkManagerAndConnect(String serverAddress, int serverPort,
            boolean useNativeTransport) {
        logger.info("createNetworkManagerAndConnect called with string: {}:{}", serverAddress, serverPort);

        // Check if this is a WebSocket address
        if (ConnectionHandler.isWebSocketAddress(serverAddress)) {
            logger.info("Detected WebSocket address, creating WebSocket connection");

            try {
                // Create WebSocket connection
                String wsAddress = ConnectionHandler.normalizeWebSocketAddress(serverAddress);
                com.aqualantic.socks.eaglercraft.socket.WebSocketNetworkManager wsManager = ConnectionHandler
                        .createWebSocketNetworkManager(wsAddress);

                // Wrap it in a NetworkManager-compatible wrapper
                // For now, we'll need to create a wrapper class
                return new WebSocketNetworkManagerWrapper(wsManager);

            } catch (Exception e) {
                logger.error("Failed to create WebSocket connection", e);
                // Fall back to showing an error
                throw new RuntimeException("Failed to connect to WebSocket server: " + e.getMessage());
            }
        }

        // Regular TCP connection
        try {
            InetAddress address = InetAddress.getByName(serverAddress);
            return NetworkManager.func_181124_a(address, serverPort, useNativeTransport);
        } catch (Exception e) {
            logger.error("Failed to resolve server address", e);
            throw new RuntimeException("Failed to resolve server address: " + e.getMessage());
        }
    }
}
