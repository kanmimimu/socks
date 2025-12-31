package com.aqualantic.socks.hooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.PacketBuffer;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.world.WorldSettings;
import io.netty.buffer.Unpooled;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SocksNetHandlerPlayClient extends NetHandlerPlayClient {

    private static final Logger logger = LogManager.getLogger();
    private final Minecraft mc;
    private final NetworkManager netManager;

    public SocksNetHandlerPlayClient(Minecraft mcIn, GuiScreen p_i46300_2_, NetworkManager p_i46300_3_,
            GameProfile p_i46300_4_) {
        super(mcIn, p_i46300_2_, p_i46300_3_, p_i46300_4_);
        this.mc = mcIn;
        this.netManager = p_i46300_3_;
    }

    @Override
    public void handleJoinGame(S01PacketJoinGame packetIn) {
        logger.info("[Socks] Handling S01PacketJoinGame (Bypassing FML logic)");

        PacketThreadUtil.checkThreadAndEnqueue(packetIn, this, this.mc);

        this.mc.playerController = new PlayerControllerMP(this.mc, this);
        WorldClient worldClient = new WorldClient(this,
                new WorldSettings(0L, packetIn.getGameType(), false, packetIn.isHardcoreMode(),
                        packetIn.getWorldType()),
                packetIn.getDimension(), packetIn.getDifficulty(), this.mc.mcProfiler);

        // Set clientWorldController (private field in NetHandlerPlayClient)
        try {
            // Need to set 'clientWorldController' field in 'NetHandlerPlayClient'
            // MCP name: clientWorldController
            // SRG name: field_147300_g (verified for 1.8)
            ReflectionHelper.setPrivateValue(NetHandlerPlayClient.class, this, worldClient, "clientWorldController",
                    "field_147300_g");
            logger.info("[Socks] Successfully set clientWorldController");
        } catch (Exception e) {
            logger.error("[Socks] Failed to set clientWorldController using ReflectionHelper", e);
        }

        this.mc.gameSettings.difficulty = packetIn.getDifficulty();
        this.mc.loadWorld(worldClient);
        this.mc.thePlayer.dimension = packetIn.getDimension();
        this.mc.displayGuiScreen(new GuiDownloadTerrain(this));
        this.mc.thePlayer.setEntityId(packetIn.getEntityId());

        // Set currentServerMaxPlayers (private field)
        try {
            // MCP name: currentServerMaxPlayers
            // SRG name: field_147299_f
            ReflectionHelper.setPrivateValue(NetHandlerPlayClient.class, this, packetIn.getMaxPlayers(),
                    "currentServerMaxPlayers", "field_147299_f");
        } catch (Exception e) {
            logger.error("[Socks] Failed to set currentServerMaxPlayers", e);
        }

        this.mc.thePlayer.setReducedDebug(packetIn.isReducedDebugInfo());

        // Send MC|Brand
        this.netManager.sendPacket(new C17PacketCustomPayload("MC|Brand",
                (new PacketBuffer(Unpooled.buffer())).writeString(ClientBrandRetriever.getClientModName())));

        logger.info("[Socks] S01PacketJoinGame handled successfully");
    }
}
