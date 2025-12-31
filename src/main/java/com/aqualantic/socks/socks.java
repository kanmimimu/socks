package com.aqualantic.socks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = socks.MODID, version = socks.VERSION, name = "Socks")
public class socks {
    public static final String MODID = "socks";
    public static final String VERSION = "1.1";

    private static final Logger logger = LogManager.getLogger("Socks");

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger.info("[Socks] Pre-initialization phase");
        logger.info("[Socks] WebSocket support enabled for EaglerCraft servers");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        logger.info("[Socks] Initialization phase");
        logger.info("[Socks] Ready to connect to WebSocket servers (ws:// or wss://)");
        logger.info("[Socks] Use 'ws://server-address/' or 'wss://server-address/' to connect");

        // WebSocket icon is now injected via ASM into
        // FMLClientHandler.enhanceServerListEntry
        // No need for event-based overlay anymore
    }
}
