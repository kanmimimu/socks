package com.aqualantic.socks.websocket;

import com.aqualantic.socks.eaglercraft.internal.EnumEaglerConnectionState;
import com.aqualantic.socks.eaglercraft.internal.IWebSocketClient;
import com.aqualantic.socks.eaglercraft.internal.IWebSocketFrame;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Implementation of IWebSocketClient using Java-WebSocket library
 */
public class JavaWebSocketClient implements IWebSocketClient {

    private final WebSocketClient client;
    private final String uri;
    private final Queue<IWebSocketFrame> binaryFrames = new LinkedList<>();
    private final Queue<IWebSocketFrame> stringFrames = new LinkedList<>();
    private volatile EnumEaglerConnectionState state = EnumEaglerConnectionState.CONNECTING;
    private boolean enableBinaryFrames = true;
    private boolean enableStringFrames = false;

    public JavaWebSocketClient(String uri) {
        this.uri = uri;
        try {
            this.client = new WebSocketClient(new URI(uri)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    state = EnumEaglerConnectionState.CONNECTED;
                    System.out.println("[Socks] WebSocket connected to: " + uri);
                }

                @Override
                public void onMessage(String message) {
                    if (enableStringFrames) {
                        synchronized (stringFrames) {
                            stringFrames.add(new StringFrame(message));
                        }
                    }
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    if (enableBinaryFrames) {
                        byte[] data = new byte[bytes.remaining()];
                        bytes.get(data);
                        synchronized (binaryFrames) {
                            binaryFrames.add(new BinaryFrame(data));
                        }
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    state = EnumEaglerConnectionState.CLOSED;
                    System.out.println("[Socks] WebSocket closed: " + reason + " (code: " + code + ")");
                }

                @Override
                public void onError(Exception ex) {
                    state = EnumEaglerConnectionState.FAILED;
                    System.err.println("[Socks] WebSocket error: " + ex.getMessage());
                    ex.printStackTrace();
                }
            };
            // Start connection asynchronously
            // This allows EaglerServerQuery to connect automatically
            // ConnectionHandler calls connectBlocking() which waits for this to complete
            this.client.connect();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create WebSocket client", e);
        }
    }

    @Override
    public EnumEaglerConnectionState getState() {
        return state;
    }

    @Override
    public boolean connectBlocking(int timeoutMS) {
        try {
            // If connect() was already called in constructor (async), just wait for it
            // Otherwise, connectBlocking() will try to call connect() again and fail with
            // "not reuseable"
            long start = System.currentTimeMillis();
            while (!client.isOpen() && !client.isClosed()) {
                Thread.sleep(50);
                if (System.currentTimeMillis() - start > timeoutMS) {
                    return false;
                }
            }
            return client.isOpen();
        } catch (InterruptedException e) {
            return false;
        }
    }

    @Override
    public boolean isOpen() {
        return client != null && client.isOpen();
    }

    @Override
    public boolean isClosed() {
        return client == null || client.isClosed();
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public int availableFrames() {
        synchronized (binaryFrames) {
            synchronized (stringFrames) {
                return binaryFrames.size() + stringFrames.size();
            }
        }
    }

    @Override
    public IWebSocketFrame getNextFrame() {
        synchronized (binaryFrames) {
            if (!binaryFrames.isEmpty()) {
                return binaryFrames.poll();
            }
        }
        synchronized (stringFrames) {
            if (!stringFrames.isEmpty()) {
                return stringFrames.poll();
            }
        }
        return null;
    }

    @Override
    public List<IWebSocketFrame> getNextFrames() {
        List<IWebSocketFrame> frames = new ArrayList<>();
        synchronized (binaryFrames) {
            frames.addAll(binaryFrames);
            binaryFrames.clear();
        }
        synchronized (stringFrames) {
            frames.addAll(stringFrames);
            stringFrames.clear();
        }
        return frames;
    }

    @Override
    public void clearFrames() {
        synchronized (binaryFrames) {
            binaryFrames.clear();
        }
        synchronized (stringFrames) {
            stringFrames.clear();
        }
    }

    @Override
    public int availableStringFrames() {
        synchronized (stringFrames) {
            return stringFrames.size();
        }
    }

    @Override
    public IWebSocketFrame getNextStringFrame() {
        synchronized (stringFrames) {
            return stringFrames.poll();
        }
    }

    @Override
    public List<IWebSocketFrame> getNextStringFrames() {
        List<IWebSocketFrame> frames = new ArrayList<>();
        synchronized (stringFrames) {
            frames.addAll(stringFrames);
            stringFrames.clear();
        }
        return frames;
    }

    @Override
    public void clearStringFrames() {
        synchronized (stringFrames) {
            stringFrames.clear();
        }
    }

    @Override
    public int availableBinaryFrames() {
        synchronized (binaryFrames) {
            return binaryFrames.size();
        }
    }

    @Override
    public IWebSocketFrame getNextBinaryFrame() {
        synchronized (binaryFrames) {
            return binaryFrames.poll();
        }
    }

    @Override
    public List<IWebSocketFrame> getNextBinaryFrames() {
        List<IWebSocketFrame> frames = new ArrayList<>();
        synchronized (binaryFrames) {
            frames.addAll(binaryFrames);
            binaryFrames.clear();
        }
        return frames;
    }

    @Override
    public void clearBinaryFrames() {
        synchronized (binaryFrames) {
            binaryFrames.clear();
        }
    }

    @Override
    public void send(String str) {
        if (client != null && client.isOpen()) {
            client.send(str);
        }
    }

    @Override
    public void send(byte[] bytes) {
        if (client != null && client.isOpen()) {
            client.send(bytes);
        }
    }

    @Override
    public String getCurrentURI() {
        return uri;
    }

    @Override
    public void setEnableStringFrames(boolean enable) {
        this.enableStringFrames = enable;
    }

    @Override
    public void setEnableBinaryFrames(boolean enable) {
        this.enableBinaryFrames = enable;
    }

    // Inner classes for frame implementations
    private static class BinaryFrame implements IWebSocketFrame {
        private final byte[] data;
        private final long timestamp;

        public BinaryFrame(byte[] data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public boolean isString() {
            return false;
        }

        @Override
        public String getString() {
            return null;
        }

        @Override
        public byte[] getByteArray() {
            return data;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(data);
        }

        @Override
        public int getLength() {
            return data.length;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }
    }

    private static class StringFrame implements IWebSocketFrame {
        private final String data;
        private final long timestamp;

        public StringFrame(String data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        @Override
        public boolean isString() {
            return true;
        }

        @Override
        public String getString() {
            return data;
        }

        @Override
        public byte[] getByteArray() {
            return data.getBytes();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(getByteArray());
        }

        @Override
        public int getLength() {
            return data.length();
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }
    }
}
