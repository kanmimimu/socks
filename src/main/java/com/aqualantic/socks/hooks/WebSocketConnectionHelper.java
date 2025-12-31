package com.aqualantic.socks.hooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;

/**
 * Helper class for WebSocket connection handling
 */
public class WebSocketConnectionHelper {

    private static final Logger logger = LogManager.getLogger("SocksHelper");

    /**
     * Extract hostname from WebSocket URL
     * ws://example.com:25565/ -> example.com
     */
    public static String extractHost(String wsUrl) {
        try {
            URI uri = new URI(wsUrl);
            String host = uri.getHost();
            if (host != null) {
                return host;
            }

            // Fallback: manual parsing
            String withoutProtocol = wsUrl.replaceFirst("^wss?://", "");
            int slashIndex = withoutProtocol.indexOf('/');
            if (slashIndex > 0) {
                withoutProtocol = withoutProtocol.substring(0, slashIndex);
            }
            int colonIndex = withoutProtocol.indexOf(':');
            if (colonIndex > 0) {
                return withoutProtocol.substring(0, colonIndex);
            }
            return withoutProtocol;

        } catch (Exception e) {
            logger.error("Failed to extract host from WebSocket URL: " + wsUrl, e);
            return "localhost";
        }
    }

    /**
     * Extract port from WebSocket URL
     * ws://example.com:25565/ -> 25565
     * wss://example.com/ -> 443 (default for wss)
     * ws://example.com/ -> 80 (default for ws)
     */
    public static int extractPort(String wsUrl) {
        try {
            URI uri = new URI(wsUrl);
            int port = uri.getPort();
            if (port > 0) {
                return port;
            }

            // Return default port based on scheme
            if (wsUrl.startsWith("wss://")) {
                return 443;
            } else {
                return 80;
            }

        } catch (Exception e) {
            logger.error("Failed to extract port from WebSocket URL: " + wsUrl, e);
            return 25565;
        }
    }

    /**
     * Check if the given address is a WebSocket URL
     */
    public static boolean isWebSocketAddress(String address) {
        return address != null && (address.startsWith("ws://") || address.startsWith("wss://"));
    }
}
