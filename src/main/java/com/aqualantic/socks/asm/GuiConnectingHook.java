package com.aqualantic.socks.asm;

import com.aqualantic.socks.network.ConnectionHandler;
import com.aqualantic.socks.hooks.WebSocketConnectionHelper;
import com.aqualantic.socks.hooks.WebSocketNetworkManagerWrapper;
import com.aqualantic.socks.hooks.SocksNetHandlerPlayClient;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.client.network.NetHandlerPlayClient;
import com.mojang.authlib.GameProfile;
import com.aqualantic.socks.eaglercraft.protocol.EaglerHandshakeHandler;

import net.minecraft.util.ChatComponentTranslation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hook methods for GuiConnecting
 * These are called from ASM-injected code
 */
public class GuiConnectingHook {

    private static final Logger logger = LogManager.getLogger("SocksHook");
    private static AtomicInteger connectionId = null;

    /**
     * Handle WebSocket connection
     * This is called from ASM-injected code in GuiConnecting.connect()
     */
    public static void connectWebSocket(final GuiConnecting gui, final String wsAddress) {
        logger.info("[Socks] WebSocket connection requested: {}", wsAddress);

        // Get CONNECTION_ID via reflection (try MCP name first, then SRG)
        if (connectionId == null) {
            try {
                Field field = getField(GuiConnecting.class, "CONNECTION_ID", "field_146372_a");
                field.setAccessible(true);
                connectionId = (AtomicInteger) field.get(null);
            } catch (Exception e) {
                logger.warn("Failed to get CONNECTION_ID, using fallback", e);
                connectionId = new AtomicInteger(0);
            }
        }

        (new Thread("WebSocket Connector #" + connectionId.incrementAndGet()) {
            public void run() {
                EaglerHandshakeHandler handshake = null;
                try {
                    // Check if cancelled
                    if (isCancelled(gui)) {
                        return;
                    }

                    logger.info("[Socks] Creating WebSocket connection to: {}", wsAddress);

                    // Create WebSocket NetworkManager
                    com.aqualantic.socks.eaglercraft.socket.WebSocketNetworkManager wsManager = ConnectionHandler
                            .createWebSocketNetworkManager(wsAddress);

                    WebSocketNetworkManagerWrapper wrapper = new WebSocketNetworkManagerWrapper(wsManager);

                    // Set the networkManager field
                    setNetworkManager(gui, wrapper);

                    // Perform EaglerCraft handshake
                    logger.info("[Socks] Performing EaglerCraft handshake...");
                    String playerName = getPlayerName(getMinecraft(gui));
                    logger.info("[Socks] Player name: {}", playerName);
                    handshake = new com.aqualantic.socks.eaglercraft.protocol.EaglerHandshakeHandler();
                    if (!performEaglerHandshake(handshake, wsManager, playerName)) {
                        throw new RuntimeException("EaglerCraft handshake failed");
                    }

                    // Extract profile info
                    // Extract profile info
                    java.util.UUID uuid = handshake.responseUUID;
                    String serverUsername = handshake.responseUsername;

                    if (uuid == null)
                        uuid = java.util.UUID.randomUUID();

                    if (serverUsername == null || serverUsername.isEmpty())
                        serverUsername = "Player";

                    logger.info("[Socks] Creating GameProfile: {} ({})", serverUsername, uuid);
                    GameProfile profile = new GameProfile(uuid, serverUsername);

                    // Set net handler to PLAY client
                    logger.info("[Socks] Switching to NetHandlerPlayClient");

                    // We use previousGuiScreen as the parent screen, similar to how it's done
                    // usually
                    wrapper.setNetHandler(new SocksNetHandlerPlayClient(
                            getMinecraft(gui),
                            getPreviousGuiScreen(gui),
                            wrapper,
                            profile));

                    // Force connection state to PLAY
                    logger.info("[Socks] Setting connection state to PLAY");
                    wrapper.setConnectionState(EnumConnectionState.PLAY);

                    logger.info("[Socks] WebSocket and handshake established!");

                } catch (Exception e) {
                    if (isCancelled(gui)) {
                        return;
                    }

                    logger.error("[Socks] WebSocket connection failed", e);

                    try {
                        // Try to go back to previous screen using reflection
                        // We need to use reflection because displayGuiScreen is obfuscated in
                        // production
                        Object mc = getMinecraft(gui);
                        if (mc != null) {
                            net.minecraft.client.gui.GuiScreen prevScreen = getPreviousGuiScreen(gui);
                            if (prevScreen != null) {
                                // Create error screen
                                net.minecraft.util.IChatComponent msg;

                                if (handshake != null && handshake.lastError != null) {
                                    try {
                                        // Try parsing as JSON using reflection
                                        msg = parseJsonMessage(handshake.lastError);
                                    } catch (Exception parseEx) {
                                        // Fallback to plain text if not JSON or parsing fails
                                        msg = new net.minecraft.util.ChatComponentText(handshake.lastError);
                                        applyChatStyleColor(msg, net.minecraft.util.EnumChatFormatting.RED);
                                    }

                                    // Add helpful hint for "online mode" error
                                    if (handshake.lastError.contains("does not support online mode")) {
                                        msg.appendText("\n\n");
                                        net.minecraft.util.IChatComponent hint = new net.minecraft.util.ChatComponentText(
                                                "Looks like you're trying to connect with valid Microsoft/Mojang Accounts,\n"
                                                        +
                                                        "Or You just logged in with Offline account but used MCID.\n\n"
                                                        +
                                                        "If you're using official Minecraft account, Please switch to Offline mode in launcher like MultiMC,\n"
                                                        +
                                                        "or if not, Please try different MCID.");
                                        applyChatStyleColor(hint, net.minecraft.util.EnumChatFormatting.GRAY);
                                        applyChatStyleItalic(hint);
                                        msg.appendSibling(hint);
                                    }
                                } else {
                                    // Default error message
                                    msg = new net.minecraft.util.ChatComponentText("EaglerCraft handshake failed");
                                    applyChatStyleColor(msg, net.minecraft.util.EnumChatFormatting.GRAY);
                                    msg.appendText("\n\n(WebSocket connection failed: " + e.getMessage() + ")");
                                }

                                net.minecraft.client.gui.GuiDisconnected errorScreen = new net.minecraft.client.gui.GuiDisconnected(
                                        prevScreen,
                                        "connect.failed",
                                        msg);

                                // mc.displayGuiScreen(errorScreen)
                                // MCP: displayGuiScreen, SRG: func_147108_a
                                Method displayMethod = getMethod(mc.getClass(), "displayGuiScreen", "func_147108_a",
                                        net.minecraft.client.gui.GuiScreen.class);
                                displayMethod.setAccessible(true);
                                displayMethod.invoke(mc, errorScreen);
                            }
                        }
                    } catch (Exception ex) {
                        logger.error("[Socks] Failed to display error screen", ex);
                    }
                }
            }
        }).start();
    }

    /**
     * Perform EaglerCraft handshake over WebSocket
     * Returns true if successful, false otherwise
     */
    private static boolean performEaglerHandshake(
            com.aqualantic.socks.eaglercraft.protocol.EaglerHandshakeHandler handshake,
            com.aqualantic.socks.eaglercraft.socket.WebSocketNetworkManager wsManager, String username) {

        try {
            // handshake instance is passed in

            // Step 1: Send CLIENT_VERSION
            logger.info("[EaglerHandshake] Step 1: Sending CLIENT_VERSION");
            wsManager.sendRawBinaryFrame(handshake.createClientVersionPacket(username));

            // Wait for SERVER_VERSION response with timeout
            com.aqualantic.socks.eaglercraft.internal.IWebSocketFrame frame = waitForFrame(wsManager, 5000);
            if (frame == null) {
                logger.error("[EaglerHandshake] Timeout waiting for SERVER_VERSION");
                return false;
            }

            if (!handshake.processServerPacket(frame.getByteArray())) {
                logger.error("[EaglerHandshake] Failed to process SERVER_VERSION");
                return false;
            }

            // Step 2: Send CLIENT_REQUEST_LOGIN
            logger.info("[EaglerHandshake] Step 2: Sending CLIENT_REQUEST_LOGIN");
            wsManager.sendRawBinaryFrame(handshake.createClientRequestLoginPacket(username, "", null));

            // Wait for SERVER_ALLOW_LOGIN response with timeout
            frame = waitForFrame(wsManager, 5000);
            if (frame == null) {
                logger.error("[EaglerHandshake] Timeout waiting for SERVER_ALLOW_LOGIN");
                return false;
            }

            if (!handshake.processServerPacket(frame.getByteArray())) {
                logger.error("[EaglerHandshake] Failed to process SERVER_ALLOW_LOGIN - server may have denied login");
                return false;
            }

            // Step 3: Send CLIENT_FINISH_LOGIN
            logger.info("[EaglerHandshake] Step 3: Sending CLIENT_FINISH_LOGIN");
            wsManager.sendRawBinaryFrame(handshake.createClientFinishLoginPacket());

            // Wait for SERVER_FINISH_LOGIN response (optional)
            frame = waitForFrame(wsManager, 2000);
            if (frame != null) {
                handshake.processServerPacket(frame.getByteArray());
            }

            logger.info("[EaglerHandshake] Handshake completed successfully!");

            return true;

        } catch (Exception e) {
            logger.error("[EaglerHandshake] Handshake failed", e);
            return false;
        }
    }

    /**
     * Wait for a WebSocket frame with timeout
     * Returns null if timeout occurs
     */
    private static com.aqualantic.socks.eaglercraft.internal.IWebSocketFrame waitForFrame(
            com.aqualantic.socks.eaglercraft.socket.WebSocketNetworkManager wsManager, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            com.aqualantic.socks.eaglercraft.internal.IWebSocketFrame frame = wsManager.getNextBinaryFrame();
            if (frame != null) {
                return frame;
            }
            try {
                Thread.sleep(10); // Small delay to avoid busy-waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null; // Timeout
    }

    // Reflection helpers to access private fields

    /**
     * Get a field by trying MCP name first, then SRG name
     * This ensures compatibility with both dev and production environments
     */
    private static Field getField(Class<?> clazz, String mcpName, String srgName) throws NoSuchFieldException {
        try {
            // Try MCP name first (dev environment)
            return clazz.getDeclaredField(mcpName);
        } catch (NoSuchFieldException e) {
            // Fall back to SRG name (production environment)
            logger.debug("[Socks] MCP name '{}' not found, trying SRG name '{}'", mcpName, srgName);
            return clazz.getDeclaredField(srgName);
        }
    }

    private static boolean isCancelled(GuiConnecting gui) {
        try {
            Field field = getField(GuiConnecting.class, "cancel", "field_146373_h");
            field.setAccessible(true);
            return field.getBoolean(gui);
        } catch (Exception e) {
            return false;
        }
    }

    private static void setNetworkManager(GuiConnecting gui, net.minecraft.network.NetworkManager nm) {
        try {
            // MCP: networkManager, SRG: field_148253_b
            Field field = null;
            try {
                field = getField(GuiConnecting.class, "networkManager", "field_148253_b");
            } catch (NoSuchFieldException e) {
                // Determine by type if name lookup fails
                for (Field f : GuiConnecting.class.getDeclaredFields()) {
                    if (f.getType() == net.minecraft.network.NetworkManager.class) {
                        field = f;
                        logger.info("[Socks] Found NetworkManager field by type: {}", f.getName());
                        break;
                    }
                }
            }

            if (field != null) {
                field.setAccessible(true);
                field.set(gui, nm);
                logger.info("[Socks] Successfully set GuiConnecting.networkManager");
            } else {
                logger.error("[Socks] Could not find networkManager field in GuiConnecting");
            }
        } catch (Exception e) {
            logger.error("Failed to set networkManager", e);
        }
    }

    private static net.minecraft.client.Minecraft getMinecraft(GuiConnecting gui) {
        try {
            Field field = getField(net.minecraft.client.gui.GuiScreen.class, "mc", "field_146297_k");
            field.setAccessible(true);
            return (net.minecraft.client.Minecraft) field.get(gui);
        } catch (Exception e) {
            logger.error("Failed to get Minecraft instance", e);
            return null;
        }
    }

    private static net.minecraft.client.gui.GuiScreen getPreviousGuiScreen(GuiConnecting gui) {
        try {
            Field field = getField(GuiConnecting.class, "previousGuiScreen", "field_146374_i");
            field.setAccessible(true);
            return (net.minecraft.client.gui.GuiScreen) field.get(gui);
        } catch (Exception e) {
            logger.error("Failed to get previousGuiScreen", e);
            return null;
        }
    }

    /**
     * Get a method by trying MCP name first, then SRG name
     * This ensures compatibility with both dev and production environments
     */
    private static Method getMethod(Class<?> clazz, String mcpName, String srgName, Class<?>... paramTypes)
            throws NoSuchMethodException {
        try {
            // Try MCP name first (dev environment)
            // Use getMethod() instead of getDeclaredMethod() to include inherited methods
            return clazz.getMethod(mcpName, paramTypes);
        } catch (NoSuchMethodException e) {
            // Fall back to SRG name (production environment)
            logger.debug("[Socks] MCP method '{}' not found, trying SRG name '{}'", mcpName, srgName);
            return clazz.getMethod(srgName, paramTypes);
        }
    }

    /**
     * Get player name from Minecraft instance using reflection
     * Handles obfuscated method names in production
     */
    private static String getPlayerName(Object mc) {
        try {
            // Get Session via reflection: mc.getSession() / mc.func_110432_I
            Method getSessionMethod = getMethod(mc.getClass(),
                    "getSession", "func_110432_I");
            getSessionMethod.setAccessible(true);
            Object session = getSessionMethod.invoke(mc);

            if (session == null) {
                logger.warn("[Socks] Session is null, using default player name");
                return "Player";
            }

            // Get GameProfile via reflection: session.getProfile() /
            // session.func_148256_e()
            Method getProfileMethod = getMethod(session.getClass(),
                    "getProfile", "func_148256_e");
            getProfileMethod.setAccessible(true);
            Object profile = getProfileMethod.invoke(session);

            if (profile == null) {
                logger.warn("[Socks] Profile is null, using default player name");
                return "Player";
            }

            // GameProfile.getName() - this should not be obfuscated (it's from Mojang's
            // authlib)
            if (profile instanceof com.mojang.authlib.GameProfile) {
                return ((com.mojang.authlib.GameProfile) profile).getName();
            }

            // Fallback: try reflection on getName
            Method getNameMethod = profile.getClass().getMethod("getName");
            return (String) getNameMethod.invoke(profile);

        } catch (Exception e) {
            logger.warn("[Socks] Failed to get player name", e);
            return "Player";
        }
    }

    // Helper to apply color to ChatComponent using reflection
    private static void applyChatStyleColor(net.minecraft.util.IChatComponent component,
            net.minecraft.util.EnumChatFormatting color) {
        try {
            // getChatStyle() - MCP: getChatStyle, SRG: func_150253_a
            Method getChatStyle = getMethod(component.getClass(), "getChatStyle", "func_150253_a");
            Object style = getChatStyle.invoke(component);

            if (style != null) {
                // Return type is ChatStyle, method is setColor(EnumChatFormatting)
                // MCP: setColor, SRG: func_150215_a
                Method setColor = getMethod(style.getClass(), "setColor", "func_150215_a",
                        net.minecraft.util.EnumChatFormatting.class);
                setColor.invoke(style, color);
            }
        } catch (Exception e) {
            logger.warn("Failed to apply chat style color via reflection", e);
        }
    }

    // Helper to apply italic style to ChatComponent using reflection
    private static void applyChatStyleItalic(net.minecraft.util.IChatComponent component) {
        try {
            // getChatStyle() - MCP: getChatStyle, SRG: func_150253_a
            Method getChatStyle = getMethod(component.getClass(), "getChatStyle", "func_150253_a");
            Object style = getChatStyle.invoke(component);

            if (style != null) {
                // Return type is ChatStyle, method is setItalic(Boolean)
                // MCP: setItalic, SRG: func_150217_b
                Method setItalic = getMethod(style.getClass(), "setItalic", "func_150217_b", Boolean.class);
                setItalic.invoke(style, Boolean.TRUE);
            }
        } catch (Exception e) {
            logger.warn("Failed to apply chat style italic via reflection", e);
        }
    }

    // Helper to parse JSON message using reflection
    private static net.minecraft.util.IChatComponent parseJsonMessage(String json) throws Exception {
        // IChatComponent.Serializer.jsonToComponent(String)
        // Class is net.minecraft.util.IChatComponent$Serializer
        Class<?> serializerClass = Class.forName("net.minecraft.util.IChatComponent$Serializer");

        // MCP: jsonToComponent, SRG: func_150699_a
        Method jsonToComponent = getMethod(serializerClass, "jsonToComponent", "func_150699_a", String.class);
        return (net.minecraft.util.IChatComponent) jsonToComponent.invoke(null, json);
    }
}
