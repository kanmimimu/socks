package com.aqualantic.socks.eaglercraft.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class QueryResponse {

    public final String responseType;
    private final JsonElement responseData;
    public final String serverVersion;
    public final String serverBrand;
    public final String serverName;
    public final long serverTime;
    public final long clientTime;
    public final boolean serverCracked;
    public final long ping;

    public QueryResponse(JsonObject obj, long ping) {
        this.responseType = obj.get("type").getAsString().toLowerCase();
        this.ping = ping;
        this.responseData = obj.get("data");
        this.serverVersion = obj.has("vers") ? obj.get("vers").getAsString() : "unknown";
        this.serverBrand = obj.has("brand") ? obj.get("brand").getAsString() : "unknown";
        this.serverName = obj.has("name") ? obj.get("name").getAsString() : "unknown";
        this.serverTime = obj.has("time") ? obj.get("time").getAsLong() : 0L;
        this.clientTime = System.currentTimeMillis();
        this.serverCracked = obj.has("cracked") && obj.get("cracked").getAsBoolean();
    }

    public boolean isResponseString() {
        return responseData.isJsonPrimitive() && responseData.getAsJsonPrimitive().isString();
    }

    public boolean isResponseJSON() {
        return responseData.isJsonObject();
    }

    public String getResponseString() {
        return responseData.getAsString();
    }

    public JsonObject getResponseJSON() {
        return responseData.getAsJsonObject();
    }

}
