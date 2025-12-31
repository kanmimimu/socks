package com.aqualantic.socks.hooks;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.aqualantic.socks.eaglercraft.socket.WebSocketNetworkManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.CryptManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.crypto.SecretKey;
import java.net.InetAddress;
import java.net.SocketAddress;

/**
 * Wrapper to make WebSocketNetworkManager compatible with Minecraft's
 * NetworkManager
 * This delegates most calls to the WebSocketNetworkManager while maintaining
 * NetworkManager interface
 */
public class WebSocketNetworkManagerWrapper extends NetworkManager {

    private static final Logger logger = LogManager.getLogger();
    private final WebSocketNetworkManager webSocketManager;

    private final io.netty.channel.Channel dummyChannel = new io.netty.channel.embedded.EmbeddedChannel(
            new io.netty.channel.ChannelHandlerAdapter() {
            });

    public WebSocketNetworkManagerWrapper(WebSocketNetworkManager webSocketManager) {
        super(EnumPacketDirection.CLIENTBOUND);
        this.webSocketManager = webSocketManager;

        // CRITICAL: Set the channel field in parent NetworkManager using reflection
        // This prevents NullPointerException in production when Minecraft accesses the
        // field directly
        try {
            java.lang.reflect.Field channelField = null;
            // Try MCP name first
            try {
                channelField = NetworkManager.class.getDeclaredField("channel");
            } catch (NoSuchFieldException e) {
                // Try SRG name for production
                channelField = NetworkManager.class.getDeclaredField("field_150746_k");
            }
            channelField.setAccessible(true);
            channelField.set(this, dummyChannel);
            logger.info("[Socks] Successfully set NetworkManager.channel field via reflection");
        } catch (Exception e) {
            logger.error("[Socks] Failed to set NetworkManager.channel field via reflection", e);
        }
    }

    @Override
    public io.netty.channel.Channel channel() {
        return dummyChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext p_channelActive_1_) throws Exception {
        // WebSocket connection is already active
        logger.info("[Socks] WebSocket channel active");
    }

    @Override
    public void setConnectionState(EnumConnectionState newState) {
        logger.info("[Socks] Setting connection state to: {}", newState);
        webSocketManager.setConnectionState(newState);
    }

    @Override
    public void channelInactive(ChannelHandlerContext p_channelInactive_1_) throws Exception {
        logger.info("[Socks] WebSocket channel inactive");
        webSocketManager.closeChannel(new ChatComponentTranslation("disconnect.endOfStream"));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext p_exceptionCaught_1_, Throwable p_exceptionCaught_2_)
            throws Exception {
        logger.error("[Socks] WebSocket exception caught", p_exceptionCaught_2_);
        webSocketManager.closeChannel(new ChatComponentTranslation("disconnect.genericReason",
                "Internal Exception: " + p_exceptionCaught_2_));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_) throws Exception {
        // Not used for WebSocket - packets are processed in processReceivedPackets
    }

    @Override
    public void setNetHandler(INetHandler handler) {
        logger.info("[Socks] Setting net handler: {}", handler);
        webSocketManager.setNetHandler(handler);
    }

    @Override
    public void sendPacket(Packet packetIn) {
        webSocketManager.sendPacket(packetIn);
    }

    // Note: varargs sendPacket is not in NetworkManager base class
    public void sendPacket(Packet packetIn, GenericFutureListener<? extends Future<? super Void>>... listeners) {
        // Send packet without listeners for now
        webSocketManager.sendPacket(packetIn);

        // Call listeners manually after sending
        if (listeners != null) {
            for (GenericFutureListener listener : listeners) {
                try {
                    listener.operationComplete(null);
                } catch (Exception e) {
                    logger.error("Error in packet listener", e);
                }
            }
        }
    }

    @Override
    public void processReceivedPackets() {
        try {
            webSocketManager.processReceivedPackets();
        } catch (Exception e) {
            logger.error("[Socks] Error processing received packets", e);
        }
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return null; // WebSocket doesn't have a traditional socket address
    }

    @Override
    public boolean isLocalChannel() {
        return true;
    }

    @Override
    public boolean isChannelOpen() {
        return webSocketManager.isChannelOpen();
    }

    @Override
    public INetHandler getNetHandler() {
        return null; // Will be retrieved from webSocketManager if needed
    }

    @Override
    public IChatComponent getExitMessage() {
        return new ChatComponentTranslation("disconnect.endOfStream");
    }

    @Override
    public void disableAutoRead() {
        // Not applicable for WebSocket
    }

    @Override
    public void setCompressionTreshold(int threshold) {
        webSocketManager.setCompressionTreshold(threshold);
    }

    // Note: checkDisconnected is not an @Override method - it's specific to our
    // implementation
    public void checkDisconnected() {
        webSocketManager.checkDisconnected();
    }

    @Override
    public void closeChannel(IChatComponent message) {
        webSocketManager.closeChannel(message);
    }

    // Note: getIsencrypted is not in NetworkManager - removed @Override
    public boolean getIsencrypted() {
        return webSocketManager.getIsencrypted();
    }

    @Override
    public void enableEncryption(SecretKey key) {
        // WebSocket encryption is handled at the protocol level (wss://)
        logger.warn("[Socks] Encryption not supported for WebSocket connections");
    }

}
