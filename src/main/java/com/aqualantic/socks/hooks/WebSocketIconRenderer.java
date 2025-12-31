package com.aqualantic.socks.hooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Helper class to render WebSocket status icon in server list
 */
public class WebSocketIconRenderer {

    private static final Logger logger = LogManager.getLogger();
    private static final ResourceLocation WEBSOCKET_ICON = new ResourceLocation(
            "socks:textures/gui/websocket_icon.png");

    // Cache for serverIP field
    private static Field serverIPField = null;
    private static boolean serverIPFieldInitialized = false;

    /**
     * Get serverIP from ServerData using reflection (handles obfuscation)
     */
    private static String getServerIP(ServerData server) {
        if (server == null) {
            return null;
        }

        try {
            if (!serverIPFieldInitialized) {
                try {
                    serverIPField = ServerData.class.getDeclaredField("serverIP");
                } catch (NoSuchFieldException e) {
                    serverIPField = ServerData.class.getDeclaredField("field_78845_b");
                }
                serverIPField.setAccessible(true);
                serverIPFieldInitialized = true;
            }
            return (String) serverIPField.get(server);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if server is a WebSocket server
     */
    public static boolean isWebSocket(ServerData server) {
        String serverIP = getServerIP(server);
        if (serverIP == null) {
            return false;
        }
        return serverIP.startsWith("ws://") || serverIP.startsWith("wss://");
    }

    /**
     * Render WebSocket icon - called from FMLClientHandler.enhanceServerListEntry
     * via ASM
     * This method receives the same coordinates that FML gets from drawEntry,
     * which already include the correct scroll offset.
     * 
     * @param entry  The ServerListEntryNormal (to access owner for tooltip)
     * @param server The ServerData
     * @param x      Left position of server entry
     * @param width  Width of the server list
     * @param y      Top position of server entry (already scroll-adjusted)
     * @param mouseX Relative mouse X (mouseX - x)
     * @param mouseY Relative mouse Y (mouseY - y)
     */
    public static void renderIconInDrawEntry(Object entry, ServerData server, int x, int width, int y, int mouseX,
            int mouseY) {
        if (!isWebSocket(server)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        // FML draws at x + width - 18
        // We draw to the left of FML at x + width - 36 < BRO YOU DON'T HAVE TO
        // Actually, Please let me draw the icon at x + width - 18 cuz there's no fml
        // icon thing at the
        // wss server list so there's no shit to do that
        int iconX = x + width - 18;
        int iconY = y + 10;
        int iconSize = 16;

        // Set up GL state
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();

        // Draw the icon
        mc.getTextureManager().bindTexture(WEBSOCKET_ICON);
        Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 0, 0, iconSize, iconSize, 16.0f, 16.0f);

        // Check if mouse is hovering over our icon
        // FML uses: relativeMouseX > width - 15 && relativeMouseX < width &&
        // relativeMouseY > 10 && relativeMouseY < 26
        // Our icon is at width - 18 to width - 20, y offset 10-26
        if (mouseX > width - 18 - 2 && mouseX < width - 18 + 16 + 2 && mouseY > 10 && mouseY < 26) {
            // Get owner (GuiMultiplayer) from ServerListEntryNormal via reflection
            try {
                // Field: owner (MCP) or field_148303_c (SRG)
                Field ownerField = null;
                try {
                    ownerField = entry.getClass().getDeclaredField("owner");
                } catch (NoSuchFieldException e) {
                    ownerField = entry.getClass().getDeclaredField("field_148303_c");
                }
                ownerField.setAccessible(true);
                Object owner = ownerField.get(entry);

                // Call setHoveringText on owner
                Method setHoveringText = null;
                try {
                    setHoveringText = owner.getClass().getMethod("setHoveringText", String.class);
                } catch (NoSuchMethodException e) {
                    setHoveringText = owner.getClass().getMethod("func_146793_a", String.class);
                }
                setHoveringText.invoke(owner, "WebSocket (EaglerCraft) Connection");
            } catch (Exception e) {
                // Silent fail - tooltip won't show but icon will
            }
        }
    }

    /**
     * Render WebSocket icon and handle tooltip directly
     * Called from ServerListEntryNormal.drawEntry via ASM (legacy method)
     */
    public static void renderIcon(
            ServerData server,
            int x, int listWidth, int y,
            int mouseX, int mouseY,
            Minecraft mc,
            Object owner) {

        if (!isWebSocket(server)) {
            return;
        }

        // Draw WebSocket icon to the left of FML icons
        // FML draws at x + width - 18, we draw at x + width - 36 <- but we don't need
        // to do that i guess?
        // int iconX = x + listWidth - 36;
        int iconX = x + listWidth - 18;
        int iconY = y + 10;
        int iconSize = 16;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(WEBSOCKET_ICON);
        Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 0, 0, iconSize, iconSize, 16.0f, 16.0f);

        // Check if mouse is hovering over icon
        if (mouseX >= iconX && mouseX < iconX + iconSize &&
                mouseY >= iconY && mouseY < iconY + iconSize) {
            // Set tooltip via reflection
            try {
                Method setHoveringText = null;
                try {
                    setHoveringText = owner.getClass().getMethod("setHoveringText", String.class);
                } catch (NoSuchMethodException e) {
                    setHoveringText = owner.getClass().getMethod("func_146793_a", String.class);
                }
                setHoveringText.invoke(owner, "WebSocket (EaglerCraft) server");
            } catch (Exception e) {
                logger.debug("Failed to set tooltip", e);
            }
        }
    }
}
