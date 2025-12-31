/*
 * Copyright (c) 2022-2025 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

package com.aqualantic.socks.eaglercraft.socket;

import java.io.IOException;
import java.util.List;

import com.aqualantic.socks.eaglercraft.internal.EnumEaglerConnectionState;
import com.aqualantic.socks.eaglercraft.internal.IWebSocketClient;
import com.aqualantic.socks.eaglercraft.internal.IWebSocketFrame;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

public class WebSocketNetworkManager extends EaglercraftNetworkManager {

    protected final IWebSocketClient webSocketClient;

    public WebSocketNetworkManager(IWebSocketClient webSocketClient) {
        super(webSocketClient.getCurrentURI());
        this.webSocketClient = webSocketClient;
    }

    public void connect() {
    }

    public EnumEaglerConnectionState getConnectStatus() {
        return webSocketClient.getState();
    }

    public void closeChannel(IChatComponent reason) {
        webSocketClient.close();
        if (nethandler != null) {
            nethandler.onDisconnect(reason);
        }
        clientDisconnected = true;
    }

    public void processReceivedPackets() throws IOException {
        if (nethandler == null)
            return;
        if (webSocketClient.availableStringFrames() > 0) {
            logger.warn("discarding {} string frames received on a binary connection",
                    webSocketClient.availableStringFrames());
            webSocketClient.clearStringFrames();
        }
        List<IWebSocketFrame> pkts = webSocketClient.getNextBinaryFrames();

        if (pkts == null) {
            return;
        }

        for (int i = 0, l = pkts.size(); i < l; ++i) {
            IWebSocketFrame next = pkts.get(i);
            ++debugPacketCounter;
            try {
                byte[] asByteArray = next.getByteArray();
                // logger.info("[Socks Debug] RX Frame: size={}, hex={}", asByteArray.length,
                // bytesToHex(asByteArray));

                ByteBuf nettyBuffer = Unpooled.buffer(asByteArray.length);
                nettyBuffer.writeBytes(asByteArray);
                PacketBuffer input = new PacketBuffer(nettyBuffer);
                int pktId = input.readVarIntFromBuffer();

                Packet pkt;
                try {
                    pkt = packetState.getPacket(EnumPacketDirection.CLIENTBOUND, pktId);
                } catch (IllegalAccessException | InstantiationException ex) {
                    throw new IOException("Received a packet with type " + pktId + " which is invalid!");
                }

                if (pkt == null) {
                    throw new IOException(
                            "Received packet type " + pktId + " which is undefined in state " + packetState);
                }

                try {
                    pkt.readPacketData(input);
                } catch (Throwable t) {
                    throw new IOException("Failed to read packet type '" + pkt.getClass().getSimpleName() + "'", t);
                }

                try {
                    pkt.processPacket(nethandler);
                } catch (Throwable t) {
                    logger.error("Failed to process {}! It'll be skipped for debug purposes.",
                            pkt.getClass().getSimpleName());
                    logger.error("Exception: ", t);
                }

            } catch (Throwable t) {
                logger.error("Failed to process websocket frame {}! It'll be skipped for debug purposes.",
                        debugPacketCounter);
                logger.error("Exception: ", t);
            }
        }
    }

    public void sendPacket(Packet pkt) {
        if (!isChannelOpen()) {
            logger.error("Packet was sent on a closed connection: {}", pkt.getClass().getSimpleName());
            return;
        }

        int i;
        try {
            i = packetState.getPacketId(EnumPacketDirection.SERVERBOUND, pkt);
        } catch (Throwable t) {
            logger.error("Incorrect packet for state: {}", pkt.getClass().getSimpleName());
            return;
        }

        temporaryBuffer.clear();
        temporaryBuffer.writeVarIntToBuffer(i);
        try {
            pkt.writePacketData(temporaryBuffer);
        } catch (IOException ex) {
            logger.error("Failed to write packet {}!", pkt.getClass().getSimpleName());
            return;
        }

        int len = temporaryBuffer.writerIndex();
        byte[] bytes = new byte[len];
        temporaryBuffer.getBytes(0, bytes);

        webSocketClient.send(bytes);
        // logger.info("[Socks Debug] TX Packet: size={}, hex={}", len,
        // bytesToHex(bytes));
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public boolean checkDisconnected() {
        if (webSocketClient.isClosed()) {
            try {
                processReceivedPackets(); // catch kick message
            } catch (IOException e) {
            }
            doClientDisconnect(new ChatComponentTranslation("disconnect.endOfStream"));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void injectRawFrame(byte[] data) {
        if (!isChannelOpen()) {
            logger.error("Frame was injected on a closed connection");
            return;
        }
        webSocketClient.send(data);
    }

    /**
     * Send raw binary frame for EaglerCraft handshake
     * This bypasses Minecraft packet encoding
     */
    public void sendRawBinaryFrame(byte[] data) {
        if (!isChannelOpen()) {
            logger.error("Frame was sent on a closed connection");
            return;
        }
        webSocketClient.send(data);
    }

    /**
     * Get the next binary frame for EaglerCraft handshake
     * Returns null if no frame is available
     */
    public IWebSocketFrame getNextBinaryFrame() {
        if (webSocketClient.availableBinaryFrames() > 0) {
            List<IWebSocketFrame> frames = webSocketClient.getNextBinaryFrames();
            if (frames != null && !frames.isEmpty()) {
                return frames.get(0);
            }

        }
        return null;
    }

}
