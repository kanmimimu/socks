package com.aqualantic.socks.hooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Event-based WebSocket icon renderer for server list.
 * Uses Forge events to draw icons after the GUI renders.
 */
public class WebSocketIconOverlay {

    private static final Logger logger = LogManager.getLogger("WebSocketIconOverlay");
    private static final ResourceLocation WEBSOCKET_ICON = new ResourceLocation(
            "socks:textures/gui/websocket_icon.png");

    private static Field serverListSelectorField;
    private static boolean reflectionInitialized = false;
    private static boolean reflectionFailed = false;
    private static int logCounter = 0;

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiMultiplayer)) {
            return;
        }

        if (reflectionFailed) {
            return;
        }

        GuiMultiplayer gui = (GuiMultiplayer) event.gui;
        Minecraft mc = Minecraft.getMinecraft();

        try {
            if (!reflectionInitialized) {
                initializeReflection();
                reflectionInitialized = true;
                logger.info("[Socks] WebSocketIconOverlay reflection initialized");
            }

            ServerSelectionList serverListSelector = (ServerSelectionList) serverListSelectorField.get(gui);
            if (serverListSelector == null) {
                return;
            }

            // Get list dimensions from public fields
            int listTop = serverListSelector.top;
            int listBottom = serverListSelector.bottom;
            int listLeft = serverListSelector.left;
            int listWidth = serverListSelector.width;
            int slotHeight = serverListSelector.slotHeight;

            // Get scroll offset
            float amountScrolled = getAmountScrolled(serverListSelector);

            // Get entries list
            List<?> entries = getListEntries(serverListSelector);
            if (entries == null) {
                if (logCounter++ % 100 == 0) {
                    logger.warn("[Socks] entries list is null");
                }
                return;
            }

            // Log once
            if (logCounter == 0) {
                logger.info(
                        "[Socks] List dims: top={}, bottom={}, left={}, width={}, slotHeight={}, scroll={}, entries={}",
                        listTop, listBottom, listLeft, listWidth, slotHeight, amountScrolled, entries.size());
            }

            // Scissor test temporarily disabled for debugging
            // GL11.glEnable(GL11.GL_SCISSOR_TEST);

            // Draw icon for each WebSocket server
            int drawn = 0;
            for (int i = 0; i < entries.size(); i++) {
                Object entry = entries.get(i);
                ServerData server = getServerFromEntry(entry);
                if (server == null || server.serverIP == null) {
                    continue;
                }

                if (!server.serverIP.startsWith("ws://") && !server.serverIP.startsWith("wss://")) {
                    continue;
                }

                // Calculate slot Y position (same as GuiSlot calculation)
                int slotTop = listTop + 4 - (int) amountScrolled + i * slotHeight;

                // Icon position
                // Move icon more to the left (away from ping icon and FML icon)
                int iconX = listLeft + listWidth - 55;
                int iconY = slotTop + 10;
                int iconSize = 16;

                if (logCounter == 0) {
                    logger.info("[Socks] Drawing icon for {} at ({}, {})", server.serverIP, iconX, iconY);
                }

                // Set up GL state for proper texture rendering
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.enableAlpha();

                // Draw icon
                mc.getTextureManager().bindTexture(WEBSOCKET_ICON);
                Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 0, 0, iconSize, iconSize, 16.0f, 16.0f);
                drawn++;

                // Tooltip
                int mouseX = event.mouseX;
                int mouseY = event.mouseY;
                if (mouseX >= iconX && mouseX < iconX + iconSize &&
                        mouseY >= iconY && mouseY < iconY + iconSize &&
                        mouseY >= listTop && mouseY <= listBottom) {
                    setTooltip(gui, "WebSocket (EaglerCraft) server");
                }
            }

            // GL11.glDisable(GL11.GL_SCISSOR_TEST);
            logCounter++;

        } catch (Exception e) {
            if (logCounter++ % 100 == 0) {
                logger.warn("[Socks] WebSocketIconOverlay error: {}", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void initializeReflection() throws Exception {
        serverListSelectorField = tryGetField(GuiMultiplayer.class, "serverListSelector", "field_146803_h");
        serverListSelectorField.setAccessible(true);
    }

    private Field tryGetField(Class<?> clazz, String mcpName, String srgName) throws Exception {
        try {
            return clazz.getDeclaredField(mcpName);
        } catch (NoSuchFieldException e) {
            return clazz.getDeclaredField(srgName);
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> getListEntries(ServerSelectionList list) {
        try {
            Field entriesField = tryGetFieldInHierarchy(list.getClass(),
                    new String[] { "listEntries", "field_148198_l" });
            if (entriesField != null) {
                entriesField.setAccessible(true);
                return (List<?>) entriesField.get(list);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private Field tryGetFieldInHierarchy(Class<?> clazz, String[] names) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException e) {
                    // Continue
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private ServerData getServerFromEntry(Object entry) {
        try {
            Field serverField = tryGetFieldInHierarchy(entry.getClass(),
                    new String[] { "server", "field_148301_e" });
            if (serverField != null) {
                serverField.setAccessible(true);
                return (ServerData) serverField.get(entry);
            }
        } catch (Exception e) {
            // Not a server entry
        }
        return null;
    }

    private float getAmountScrolled(ServerSelectionList list) {
        try {
            // Try method first
            Method method = tryGetMethodInHierarchy(list.getClass(),
                    new String[] { "getAmountScrolled", "func_148148_g" });
            if (method != null) {
                method.setAccessible(true);
                return (Float) method.invoke(list);
            }

            // Fallback: try direct field access
            Field scrollField = tryGetFieldInHierarchy(list.getClass(),
                    new String[] { "amountScrolled", "field_148169_q" });
            if (scrollField != null) {
                scrollField.setAccessible(true);
                return scrollField.getFloat(list);
            }
        } catch (Exception e) {
            // Ignore
        }
        return 0.0f;
    }

    private Method tryGetMethodInHierarchy(Class<?> clazz, String[] names) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (String name : names) {
                try {
                    return current.getDeclaredMethod(name);
                } catch (NoSuchMethodException e) {
                    // Continue
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void setTooltip(GuiMultiplayer gui, String text) {
        try {
            Method m = tryGetMethodInHierarchy(gui.getClass(),
                    new String[] { "setHoveringText", "func_146793_a" });
            if (m != null && m.getParameterTypes().length == 1) {
                m.invoke(gui, text);
            }
        } catch (Exception e) {
            // Silent
        }
    }
}
