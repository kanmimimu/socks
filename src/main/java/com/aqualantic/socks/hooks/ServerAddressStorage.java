package com.aqualantic.socks.hooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores original server addresses before Minecraft processes them
 * This allows us to intercept WebSocket URLs before they're parsed as host:port
 */
public class ServerAddressStorage {

    private static final Logger logger = LogManager.getLogger("SocksStorage");

    // Thread-safe storage for server addresses
    private static final Map<Thread, String> serverAddresses = new ConcurrentHashMap<>();

    /**
     * Store the original server address for the current thread
     */
    public static void storeServerAddress(String address) {
        Thread currentThread = Thread.currentThread();
        logger.info("[Socks] Storing server address for thread {}: {}", currentThread.getName(), address);
        serverAddresses.put(currentThread, address);
    }

    /**
     * DEBUG: Dump all fields from a ServerData object to find the correct SRG field
     * name
     */
    public static void debugDumpServerData(Object serverData) {
        if (serverData == null) {
            logger.info("[Socks DEBUG] ServerData is null");
            return;
        }

        logger.info("[Socks DEBUG] ========== ServerData Fields Dump ==========");
        logger.info("[Socks DEBUG] Class: {}", serverData.getClass().getName());

        Field[] fields = serverData.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(serverData);
                String valueStr = (value != null) ? value.toString() : "null";
                // Truncate long values
                if (valueStr.length() > 100) {
                    valueStr = valueStr.substring(0, 100) + "...";
                }
                logger.info("[Socks DEBUG] Field: {} (type: {}) = {}",
                        field.getName(), field.getType().getSimpleName(), valueStr);
            } catch (Exception e) {
                logger.info("[Socks DEBUG] Field: {} (type: {}) = <error reading>",
                        field.getName(), field.getType().getSimpleName());
            }
        }
        logger.info("[Socks DEBUG] ========== End of Dump ==========");
    }

    /**
     * Retrieve and remove the stored server address for the current thread
     */
    public static String getAndClearServerAddress() {
        Thread currentThread = Thread.currentThread();
        String address = serverAddresses.remove(currentThread);
        logger.info("[Socks] Retrieved server address for thread {}: {}", currentThread.getName(), address);
        return address;
    }

    /**
     * Check if a server address is stored for the current thread
     */
    public static boolean hasStoredAddress() {
        return serverAddresses.containsKey(Thread.currentThread());
    }
}
