package com.aqualantic.socks.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.aqualantic.socks.eaglercraft.internal.EnumServerRateLimit;
import com.aqualantic.socks.eaglercraft.internal.QueryReadyState;
import com.aqualantic.socks.eaglercraft.internal.QueryResponse;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.util.EnumChatFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class CustomServerPinger extends OldServerPinger {

    private static final Logger logger = LogManager.getLogger("CustomServerPinger");

    // Wrapper class to associate query with server data
    private static class ActiveQuery {
        final EaglerServerQuery query;
        final ServerData server;
        long pingSentTime;
        boolean hasPing;
        boolean iconEnabled;
        boolean iconReceived;

        ActiveQuery(EaglerServerQuery query, ServerData server) {
            this.query = query;
            this.server = server;
            this.pingSentTime = System.currentTimeMillis();
            this.hasPing = false;
            this.iconEnabled = false;
            this.iconReceived = false;
        }
    }

    private final List<ActiveQuery> activeQueries = Collections.synchronizedList(new LinkedList<ActiveQuery>());

    @Override
    public void ping(ServerData server) throws UnknownHostException {
        // Debug: Log every ping call
        logger.info("[Socks] CustomServerPinger.ping() called");
        if (server == null) {
            logger.warn("[Socks] ping() called with null ServerData!");
            return;
        }
        logger.info("[Socks] ServerData serverIP: {}", server.serverIP);

        if (server.serverIP != null && (server.serverIP.startsWith("ws://") || server.serverIP.startsWith("wss://"))) {
            // EaglerCraft Ping
            logger.info("[Socks] Starting EaglerCraft ping for: {}", server.serverIP);
            server.serverMOTD = EnumChatFormatting.GRAY + "Pinging...";
            server.pingToServer = -1L;
            server.playerList = null;

            try {
                // IMPORTANT: Ensure we request the MOTD which triggers the icon if available
                EaglerServerQuery query = new EaglerServerQuery(server.serverIP, "MOTD");
                synchronized (activeQueries) {
                    activeQueries.add(new ActiveQuery(query, server));
                }
                logger.info("[Socks] EaglerCraft query added to active list");
            } catch (Exception e) {
                server.serverMOTD = EnumChatFormatting.DARK_RED + "Can't connect to server.";
                server.populationInfo = "";
                logger.error("Failed to start EaglerCraft ping", e);
            }
        } else {
            // Vanilla Ping
            super.ping(server);
        }
    }

    @Override
    public void pingPendingNetworks() {
        // Handle vanilla pings
        super.pingPendingNetworks();

        // Handle EaglerCraft pings
        synchronized (activeQueries) {
            if (!activeQueries.isEmpty()) {
                logger.info("[Socks] Processing {} active EaglerCraft queries", activeQueries.size());
            }
            Iterator<ActiveQuery> it = activeQueries.iterator();
            while (it.hasNext()) {
                ActiveQuery act = it.next();
                EaglerServerQuery query = act.query;
                ServerData server = act.server;

                query.update();

                logger.info("[Socks] Query state: readyState={}, responsesAvailable={}, binaryAvailable={}",
                        query.readyState(), query.responsesAvailable(), query.binaryResponsesAvailable());

                // Check for rate limiting
                if (query.getRateLimit() == EnumServerRateLimit.BLOCKED) {
                    server.serverMOTD = EnumChatFormatting.RED + "Rate Limited (Blocked)";
                    query.close();
                    it.remove();
                    continue;
                } else if (query.getRateLimit() == EnumServerRateLimit.LOCKED_OUT) {
                    server.serverMOTD = EnumChatFormatting.RED + "Rate Limited (Locked Out)";
                    query.close();
                    it.remove();
                    continue;
                }

                // Process MOTD responses
                if (query.responsesAvailable() > 0) {
                    QueryResponse resp = query.getResponse();

                    if (!act.hasPing) {
                        server.pingToServer = resp.ping;
                        act.hasPing = true;
                    }

                    if (resp.isResponseJSON()) {
                        JsonObject data = resp.getResponseJSON();

                        // MOTD
                        if (data.has("motd")) {
                            JsonArray motd = data.getAsJsonArray("motd");
                            if (motd.size() > 0) {
                                server.serverMOTD = motd.get(0).getAsString();
                                if (motd.size() > 1) {
                                    server.serverMOTD += "\n" + motd.get(1).getAsString();
                                }
                            }
                        }

                        // Population
                        int online = data.has("online") ? data.get("online").getAsInt() : 0;
                        int max = data.has("max") ? data.get("max").getAsInt() : 0;
                        server.populationInfo = EnumChatFormatting.GRAY + "" + online +
                                EnumChatFormatting.DARK_GRAY + "/" +
                                EnumChatFormatting.GRAY + max;

                        // Player List (Tooltip)
                        if (data.has("players")) {
                            JsonArray players = data.getAsJsonArray("players");
                            StringBuilder sb = new StringBuilder();
                            for (JsonElement p : players) {
                                if (sb.length() > 0)
                                    sb.append("\n");
                                if (p.isJsonObject()) {
                                    sb.append(p.getAsJsonObject().get("name").getAsString());
                                } else {
                                    sb.append(p.getAsString());
                                }
                            }
                            server.playerList = sb.toString();
                        }

                        // Check if icon is enabled
                        if (data.has("icon") && data.get("icon").getAsBoolean()) {
                            act.iconEnabled = true;
                        }
                    }
                }

                // Process binary icon data
                if (query.binaryResponsesAvailable() > 0) {
                    byte[] iconBytes = query.getBinaryResponse();

                    if (act.iconEnabled && iconBytes != null && iconBytes.length == 16384) {
                        try {
                            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                            int[] pixels = new int[64 * 64];

                            // Convert RGBA bytes to ARGB ints
                            for (int i = 0; i < 4096; i++) {
                                int j = i * 4;
                                int r = iconBytes[j] & 0xFF;
                                int g = iconBytes[j + 1] & 0xFF;
                                int b = iconBytes[j + 2] & 0xFF;
                                int a = iconBytes[j + 3] & 0xFF;

                                pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
                            }

                            image.setRGB(0, 0, 64, 64, pixels, 0, 64);

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(image, "png", baos);
                            byte[] pngBytes = baos.toByteArray();

                            String base64Icon = Base64.getEncoder().encodeToString(pngBytes);
                            server.setBase64EncodedIconData(base64Icon);

                            act.iconReceived = true;

                        } catch (Exception e) {
                            logger.error("Failed to process server icon", e);
                        }
                    } else {
                        if (iconBytes != null) {
                            logger.warn("Received binary packet: enabled={}, size={}", act.iconEnabled,
                                    iconBytes.length);
                        }
                    }
                }

                // Determine if we should close the query
                boolean shouldClose = false;

                // Close if we got icon (when enabled) or icon not enabled and we got MOTD
                if (act.hasPing) {
                    if (act.iconEnabled) {
                        // Wait for icon
                        if (act.iconReceived) {
                            shouldClose = true;
                        }
                    } else {
                        // No icon expected, can close after MOTD
                        shouldClose = true;
                    }
                }

                // Also close if connection failed or timeout (2 seconds)
                if (!query.readyState().equals(QueryReadyState.OPEN)
                        && !query.readyState().equals(QueryReadyState.CONNECTING)) {
                    shouldClose = true;
                }

                long elapsed = System.currentTimeMillis() - act.pingSentTime;
                if (elapsed > 2000 && act.hasPing) {
                    // Timeout
                    shouldClose = true;
                }

                if (shouldClose) {
                    if (!act.hasPing && server.serverMOTD.equals(EnumChatFormatting.GRAY + "Pinging...")) {
                        server.serverMOTD = EnumChatFormatting.DARK_RED + "Can't connect to server.";
                        server.populationInfo = "";
                    }
                    query.close();
                    it.remove();
                }
            }
        }
    }

    @Override
    public void clearPendingNetworks() {
        super.clearPendingNetworks();
        synchronized (activeQueries) {
            Iterator<ActiveQuery> it = activeQueries.iterator();
            while (it.hasNext()) {
                ActiveQuery act = it.next();
                act.query.close();
                it.remove();
            }
        }
    }
}
