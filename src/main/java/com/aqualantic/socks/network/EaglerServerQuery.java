package com.aqualantic.socks.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.aqualantic.socks.eaglercraft.internal.*;
import com.aqualantic.socks.platform.PlatformNetworking;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

public class EaglerServerQuery {

    public static final Logger logger = LogManager.getLogger("EaglerServerQuery");

    private final List<QueryResponse> queryResponses = new LinkedList<>();
    private final List<byte[]> queryResponsesBytes = new LinkedList<>();

    protected final IWebSocketClient websocketClient;
    protected final String uri;
    protected final String accept;
    protected boolean hasSentAccept = false;
    protected boolean open = true;
    protected boolean alive = false;
    protected long pingStart = -1L;
    protected long pingTimer = -1L;
    private EnumServerRateLimit rateLimit = EnumServerRateLimit.OK;

    public EaglerServerQuery(String uri, String accept) {
        this.websocketClient = PlatformNetworking.openWebSocket(uri);
        this.websocketClient.setEnableStringFrames(true);
        this.uri = uri;
        this.accept = accept;
        this.pingStart = System.currentTimeMillis();
    }

    public void update() {
        if(!hasSentAccept && websocketClient.getState() == EnumEaglerConnectionState.CONNECTED) {
            hasSentAccept = true;
            websocketClient.send("Accept: " + accept);
        }
        
        List<IWebSocketFrame> lst = websocketClient.getNextFrames();
        if(lst != null) {
            for(int i = 0, l = lst.size(); i < l; ++i) {
                IWebSocketFrame frame = lst.get(i);
                alive = true;
                if(pingTimer == -1) {
                    pingTimer = frame.getTimestamp() - pingStart;
                    if(pingTimer < 1) {
                        pingTimer = 1;
                    }
                }
                if(frame.isString()) {
                    String str = frame.getString();
                    if(str.equalsIgnoreCase("BLOCKED")) {
                        logger.error("Reached full IP ratelimit for {}!", uri);
                        rateLimit = EnumServerRateLimit.BLOCKED;
                        return;
                    }
                    if(str.equalsIgnoreCase("LOCKED")) {
                        logger.error("Reached full IP ratelimit lockout for {}!", uri);
                        rateLimit = EnumServerRateLimit.LOCKED_OUT;
                        return;
                    }
                    try {
                        JsonObject obj = new JsonParser().parse(str).getAsJsonObject();
                        String type = obj.has("type") ? obj.get("type").getAsString() : null;
                        
                        if("blocked".equalsIgnoreCase(type)) {
                            logger.error("Reached query ratelimit for {}!", uri);
                            rateLimit = EnumServerRateLimit.BLOCKED;
                        } else if("locked".equalsIgnoreCase(type)) {
                            logger.error("Reached query ratelimit lockout for {}!", uri);
                            rateLimit = EnumServerRateLimit.LOCKED_OUT;
                        } else {
                            queryResponses.add(new QueryResponse(obj, pingTimer));
                        }
                    } catch(Throwable t) {
                        logger.error("Exception thrown parsing websocket query response from \"" + uri + "\"!");
                        logger.error(t);
                    }
                } else {
                    queryResponsesBytes.add(frame.getByteArray());
                }
            }
        }
        
        if(websocketClient.isClosed()) {
            open = false;
        }
    }

    public void close() {
        if(open) {
            open = false;
            websocketClient.close();
        }
    }
    
    // Additional accessors matching ServerQueryImpl logic
    
    public int responsesAvailable() {
        synchronized(queryResponses) {
            return queryResponses.size();
        }
    }

    public QueryResponse getResponse() {
        synchronized(queryResponses) {
            if(queryResponses.size() > 0) {
                return queryResponses.remove(0);
            } else {
                return null;
            }
        }
    }

    public int binaryResponsesAvailable() {
        synchronized(queryResponsesBytes) {
            return queryResponsesBytes.size();
        }
    }

    public byte[] getBinaryResponse() {
        synchronized(queryResponsesBytes) {
            if(queryResponsesBytes.size() > 0) {
                return queryResponsesBytes.remove(0);
            } else {
                return null;
            }
        }
    }

    public QueryReadyState readyState() {
        return open ? (alive ? QueryReadyState.OPEN : QueryReadyState.CONNECTING)
                : (alive ? QueryReadyState.CLOSED : QueryReadyState.FAILED);
    }
    
    public EnumServerRateLimit getRateLimit() {
        return rateLimit;
    }
}
