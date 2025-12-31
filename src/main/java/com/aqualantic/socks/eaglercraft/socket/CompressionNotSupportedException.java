package com.aqualantic.socks.eaglercraft.socket;

/**
 * Exception thrown when compression is not supported
 */
public class CompressionNotSupportedException extends RuntimeException {
    public CompressionNotSupportedException() {
        super("Compression is not supported in EaglerCraft WebSocket connections");
    }
}
