package com.aqualantic.socks.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * FML Core Mod for Socks - enables WebSocket connection support
 */
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.Name("SocksCoremod")
@IFMLLoadingPlugin.TransformerExclusions("com.aqualantic.socks.asm")
public class SocksCoremod implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { "com.aqualantic.socks.asm.SocksClassTransformer" };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // No data injection needed
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
