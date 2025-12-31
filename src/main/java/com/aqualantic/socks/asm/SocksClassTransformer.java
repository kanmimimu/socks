package com.aqualantic.socks.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/**
 * ASM Class Transformer for intercepting Minecraft's connection logic
 * This transformer hooks GuiConnecting to intercept WebSocket connections
 */
public class SocksClassTransformer implements IClassTransformer {

    // Environment detection - cached for performance
    private static Boolean isObfuscated = null;
    private static String serverIPFieldName = null;

    /**
     * Detect if we're running in an obfuscated environment
     * by checking if a known MCP field name exists
     */
    private static boolean isObfuscatedEnvironment() {
        if (isObfuscated == null) {
            try {
                // Try to find MCP name - if it exists, we're in dev
                Class.forName("net.minecraft.client.multiplayer.ServerData")
                        .getDeclaredField("serverIP");
                isObfuscated = false;
                System.out.println("[Socks] Detected DEVELOPMENT environment (MCP names)");
            } catch (NoSuchFieldException e) {
                isObfuscated = true;
                System.out.println("[Socks] Detected PRODUCTION environment (SRG names)");
            } catch (ClassNotFoundException e) {
                isObfuscated = true;
                System.out.println("[Socks] Class not found, assuming PRODUCTION environment");
            }
        }
        return isObfuscated;
    }

    /**
     * Get the appropriate field name for ServerData.serverIP
     */
    private static String getServerIPFieldName() {
        if (serverIPFieldName == null) {
            serverIPFieldName = isObfuscatedEnvironment() ? "field_78845_b" : "serverIP";
            System.out.println("[Socks] Using field name: " + serverIPFieldName + " for ServerData.serverIP");
        }
        return serverIPFieldName;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        // Transform GuiConnecting class to intercept connection creation
        if ("net.minecraft.client.multiplayer.GuiConnecting".equals(transformedName)) {
            System.out.println("[Socks] Transforming GuiConnecting class");
            return transformGuiConnecting(basicClass);
        }
        // Transform GuiMultiplayer - check both deobf and obf names
        // azh = GuiMultiplayer (NOT axv, which is GuiSelectWorld)
        if ("net.minecraft.client.gui.GuiMultiplayer".equals(transformedName) || "azh".equals(name)) {
            System.out.println("[Socks] Transforming GuiMultiplayer class (name=" + name + ", transformedName="
                    + transformedName + ")");
            return transformGuiMultiplayer(basicClass);
        }
        // Transform FMLClientHandler to hook into enhanceServerListEntry
        // This allows us to draw WebSocket icon after FML's icon, using the same
        // coordinates
        if ("net.minecraftforge.fml.client.FMLClientHandler".equals(transformedName)) {
            System.out.println("[Socks] Transforming FMLClientHandler class");
            return transformFMLClientHandler(basicClass);
        }
        return basicClass;
    }

    private byte[] transformFMLClientHandler(byte[] basicClass) {
        ClassReader classReader = new ClassReader(basicClass);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor = new FMLClientHandlerVisitor(Opcodes.ASM5, classWriter);
        classReader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }

    private byte[] transformServerListEntryNormal(byte[] basicClass) {
        ClassReader classReader = new ClassReader(basicClass);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor = new ServerListEntryNormalVisitor(Opcodes.ASM5, classWriter);
        // Use SKIP_FRAMES to force ASM to recalculate all stack map frames
        // This is needed because drawEntry has multiple RETURN points with different
        // stack states
        classReader.accept(classVisitor, ClassReader.SKIP_FRAMES);
        return classWriter.toByteArray();
    }

    private byte[] transformGuiMultiplayer(byte[] basicClass) {
        ClassReader classReader = new ClassReader(basicClass);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor = new GuiMultiplayerVisitor(Opcodes.ASM5, classWriter);
        classReader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }

    private byte[] transformGuiConnecting(byte[] basicClass) {
        // Detect environment before transformation
        String fieldName = getServerIPFieldName();

        ClassReader classReader = new ClassReader(basicClass);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor classVisitor = new GuiConnectingVisitor(Opcodes.ASM5, classWriter, fieldName);
        classReader.accept(classVisitor, 0);
        return classWriter.toByteArray();
    }

    private static class GuiConnectingVisitor extends ClassVisitor {

        private final String serverIPFieldName;

        public GuiConnectingVisitor(int api, ClassVisitor cv, String serverIPFieldName) {
            super(api, cv);
            this.serverIPFieldName = serverIPFieldName;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

            // DEBUG: Log ALL constructors
            if ("<init>".equals(name)) {
                System.out.println("[Socks DEBUG] Found constructor: " + desc);
            }

            // Hook the constructor that takes (GuiScreen, Minecraft, ServerData)
            if ("<init>".equals(name) && isServerDataConstructor(desc)) {
                System.out.println("[Socks] Hooking GuiConnecting constructor: " + desc);
                return new ConstructorVisitor(api, mv, serverIPFieldName);
            }

            // Hook connect method to intercept before InetAddress.getByName
            // DEV: method name is "connect"
            // PROD: method name is obfuscated (e.g., "a")
            if ("(Ljava/lang/String;I)V".equals(desc)) {
                boolean shouldHook = false;

                if (!isObfuscatedEnvironment()) {
                    // DEV environment - use MCP name
                    shouldHook = "connect".equals(name);
                } else {
                    // PROD environment - use short obfuscated name (1-2 chars)
                    shouldHook = name.length() <= 2;
                }

                if (shouldHook) {
                    System.out.println("[Socks] Hooking connect method: " + name + " " + desc);
                    return new ConnectMethodVisitor(api, mv);
                }
            }

            return mv;
        }

        /**
         * Check if this constructor is the one that takes ServerData
         * Pattern: (LGuiScreen;LMinecraft;LServerData;)V = 3 object parameters
         * In obfuscated: (Lxxx;Lyyy;Lzzz;)V
         */
        private boolean isServerDataConstructor(String desc) {
            // Count object parameters (Lxxx;)
            int objCount = 0;
            int i = 1; // Skip opening (
            while (i < desc.length() && desc.charAt(i) != ')') {
                if (desc.charAt(i) == 'L') {
                    objCount++;
                    // Skip to semicolon
                    while (i < desc.length() && desc.charAt(i) != ';')
                        i++;
                }
                i++;
            }

            // The ServerData constructor has exactly 3 object parameters
            // and is not the simpler (GuiScreen, Minecraft, String, int) constructor
            boolean has3Objects = (objCount == 3);
            boolean hasNoStringOrInt = !desc.contains("Ljava/lang/String;") && !desc.contains("I)");

            System.out.println("[Socks DEBUG] Constructor analysis: objCount=" + objCount +
                    ", has3Objects=" + has3Objects + ", hasNoStringOrInt=" + hasNoStringOrInt);

            return has3Objects && hasNoStringOrInt;
        }
    }

    private static class ConstructorVisitor extends MethodVisitor {

        private boolean hasInjected = false;
        private final String serverIPFieldName;

        public ConstructorVisitor(int api, MethodVisitor mv, String serverIPFieldName) {
            super(api, mv);
            this.serverIPFieldName = serverIPFieldName;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);

            // After calling super constructor, inject our storage code
            if (!hasInjected && opcode == Opcodes.INVOKESPECIAL && "<init>".equals(name)) {
                System.out.println("[Socks] Injecting server address storage after super() call");
                System.out.println("[Socks] Using field name: " + serverIPFieldName);

                // DEBUG: First, dump all ServerData fields to find the correct one
                super.visitVarInsn(Opcodes.ALOAD, 3); // Load ServerData
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/aqualantic/socks/hooks/ServerAddressStorage",
                        "debugDumpServerData",
                        "(Ljava/lang/Object;)V",
                        false);

                // Load the ServerData parameter (ALOAD 3)
                super.visitVarInsn(Opcodes.ALOAD, 3);
                // Get the serverIP field (using dynamic field name for dev/prod support)
                super.visitFieldInsn(Opcodes.GETFIELD,
                        "net/minecraft/client/multiplayer/ServerData",
                        serverIPFieldName,
                        "Ljava/lang/String;");
                // Call our storage method
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/aqualantic/socks/hooks/ServerAddressStorage",
                        "storeServerAddress",
                        "(Ljava/lang/String;)V",
                        false);

                hasInjected = true;
            }
        }
    }

    private static class ConnectMethodVisitor extends MethodVisitor {

        private boolean hasInjected = false;

        public ConnectMethodVisitor(int api, MethodVisitor mv) {
            super(api, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();

            // Inject WebSocket check at the very beginning of the connect method
            System.out.println("[Socks] Injecting WebSocket check at beginning of connect()");

            // Get stored server address from ServerAddressStorage
            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/aqualantic/socks/hooks/ServerAddressStorage",
                    "getAndClearServerAddress",
                    "()Ljava/lang/String;",
                    false);

            // Store in local variable 3 (after this, ip, port parameters = 0, 1, 2)
            super.visitVarInsn(Opcodes.ASTORE, 3);

            // Check if we have a stored address
            super.visitVarInsn(Opcodes.ALOAD, 3);
            Label normalFlow = new Label();
            super.visitJumpInsn(Opcodes.IFNULL, normalFlow);

            // Check if it's a WebSocket address
            super.visitVarInsn(Opcodes.ALOAD, 3);
            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/aqualantic/socks/hooks/WebSocketConnectionHelper",
                    "isWebSocketAddress",
                    "(Ljava/lang/String;)Z",
                    false);
            super.visitJumpInsn(Opcodes.IFEQ, normalFlow);

            // It's a WebSocket! Call our custom handler
            super.visitVarInsn(Opcodes.ALOAD, 0); // this
            super.visitVarInsn(Opcodes.ALOAD, 3); // originalAddress
            super.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/aqualantic/socks/asm/GuiConnectingHook",
                    "connectWebSocket",
                    "(Lnet/minecraft/client/multiplayer/GuiConnecting;Ljava/lang/String;)V",
                    false);

            // Return early
            super.visitInsn(Opcodes.RETURN);

            // Normal flow continues here
            super.visitLabel(normalFlow);
            super.visitFrame(Opcodes.F_APPEND, 1, new Object[] { "java/lang/String" }, 0, null);

            hasInjected = true;
        }
    }

    private static class GuiMultiplayerVisitor extends ClassVisitor {
        public GuiMultiplayerVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            if (name.equals("<init>")) { // Constructor
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW) {
                            System.out.println("[Socks DEBUG] GuiMultiplayer.<init> NEW instruction: " + type);
                        }
                        if (opcode == Opcodes.NEW && (type.equals("net/minecraft/client/network/OldServerPinger")
                                || type.equals("bdg"))) {
                            System.out.println(
                                    "[Socks] Replacing OldServerPinger with CustomServerPinger in GuiMultiplayer");
                            super.visitTypeInsn(opcode, "com/aqualantic/socks/network/CustomServerPinger");
                        } else {
                            super.visitTypeInsn(opcode, type);
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        if (opcode == Opcodes.INVOKESPECIAL
                                && (owner.equals("net/minecraft/client/network/OldServerPinger") || owner.equals("bdg"))
                                && name.equals("<init>")) {
                            super.visitMethodInsn(opcode, "com/aqualantic/socks/network/CustomServerPinger", name, desc,
                                    itf);
                        } else {
                            super.visitMethodInsn(opcode, owner, name, desc, itf);
                        }
                    }
                };
            }
            return mv;
        }
    }

    /**
     * Visitor for FMLClientHandler to inject WebSocket icon rendering into
     * enhanceServerListEntry
     * FML's method receives the correct x, width, y coordinates from drawEntry, so
     * we can use them directly.
     */
    private static class FMLClientHandlerVisitor extends ClassVisitor {
        public FMLClientHandlerVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

            // enhanceServerListEntry(ServerListEntryNormal, ServerData, int x, int width,
            // int y, int relativeMouseX, int relativeMouseY)
            // Descriptor:
            // (Lnet/minecraft/client/gui/ServerListEntryNormal;Lnet/minecraft/client/multiplayer/ServerData;IIIII)Ljava/lang/String;
            if (name.equals("enhanceServerListEntry")) {
                System.out.println(
                        "[Socks] Injecting WebSocket icon rendering into FMLClientHandler.enhanceServerListEntry");
                return new EnhanceServerListEntryVisitor(Opcodes.ASM5, mv);
            }
            return mv;
        }
    }

    /**
     * Method visitor to inject WebSocket icon rendering at the start of
     * enhanceServerListEntry
     */
    private static class EnhanceServerListEntryVisitor extends MethodVisitor {
        public EnhanceServerListEntryVisitor(int api, MethodVisitor mv) {
            super(api, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            // Inject at the start of the method:
            // WebSocketIconRenderer.renderIconInDrawEntry(serverListEntry, serverEntry, x,
            // width, y, mouseX, mouseY);
            // Method params:
            // 0: this (FMLClientHandler)
            // 1: ServerListEntryNormal serverListEntry
            // 2: ServerData serverEntry
            // 3: int x
            // 4: int width
            // 5: int y
            // 6: int relativeMouseX
            // 7: int relativeMouseY

            // Load serverListEntry (ServerListEntryNormal) - slot 1
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            // Load serverEntry (ServerData) - slot 2
            mv.visitVarInsn(Opcodes.ALOAD, 2);
            // Load x - slot 3
            mv.visitVarInsn(Opcodes.ILOAD, 3);
            // Load width - slot 4
            mv.visitVarInsn(Opcodes.ILOAD, 4);
            // Load y - slot 5
            mv.visitVarInsn(Opcodes.ILOAD, 5);
            // Load relativeMouseX - slot 6
            mv.visitVarInsn(Opcodes.ILOAD, 6);
            // Load relativeMouseY - slot 7
            mv.visitVarInsn(Opcodes.ILOAD, 7);

            // Call WebSocketIconRenderer.renderIconInDrawEntry(Object entry, ServerData,
            // int x, int width, int y, int mouseX, int mouseY)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/aqualantic/socks/hooks/WebSocketIconRenderer",
                    "renderIconInDrawEntry",
                    "(Ljava/lang/Object;Lnet/minecraft/client/multiplayer/ServerData;IIIII)V",
                    false);

            System.out.println("[Socks] Injected WebSocket icon call at start of enhanceServerListEntry");
        }
    }

    /**
     * Visitor for ServerListEntryNormal to inject WebSocket icon rendering
     */
    private static class ServerListEntryNormalVisitor extends ClassVisitor {
        private String className;

        public ServerListEntryNormalVisitor(int api, ClassVisitor cv) {
            super(api, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

            // drawEntry method: (IIIIIIIZ)V in both MCP and obfuscated
            // MCP: drawEntry, SRG: func_180790_a
            // Parameters: slotIndex, x, y, listWidth, slotHeight, mouseX, mouseY,
            // isSelected
            if ((name.equals("drawEntry") || name.equals("func_180790_a")) && desc.equals("(IIIIIIIZ)V")) {
                System.out.println("[Socks] Injecting WebSocket icon rendering into ServerListEntryNormal.drawEntry");
                return new DrawEntryMethodVisitor(Opcodes.ASM5, mv, className);
            }
            return mv;
        }
    }

    /**
     * Method visitor to inject WebSocket icon rendering at the end of drawEntry
     */
    private static class DrawEntryMethodVisitor extends MethodVisitor {
        private final String className;

        public DrawEntryMethodVisitor(int api, MethodVisitor mv, String className) {
            super(api, mv);
            this.className = className;
        }

        @Override
        public void visitInsn(int opcode) {
            // Inject before RETURN instruction
            if (opcode == Opcodes.RETURN) {
                System.out.println("[Socks] Injecting WebSocket icon call before RETURN");
                injectWebSocketIconRendering();
            }
            super.visitInsn(opcode);
        }

        private void injectWebSocketIconRendering() {
            // Get field names based on environment
            // MCP: server, SRG: field_148301_e
            String serverFieldName = isObfuscatedEnvironment() ? "field_148301_e" : "server";
            // MCP: mc, SRG: field_148300_d
            String mcFieldName = isObfuscatedEnvironment() ? "field_148300_d" : "mc";
            // MCP: owner (GuiMultiplayer), SRG: field_148303_c
            String ownerFieldName = isObfuscatedEnvironment() ? "field_148303_c" : "owner";

            // ServerData class
            String serverDataClass = isObfuscatedEnvironment() ? "bde" : "net/minecraft/client/multiplayer/ServerData";
            // Minecraft class
            String minecraftClass = isObfuscatedEnvironment() ? "ave" : "net/minecraft/client/Minecraft";
            // GuiMultiplayer class
            String guiMultiplayerClass = isObfuscatedEnvironment() ? "azh" : "net/minecraft/client/gui/GuiMultiplayer";

            // Call: WebSocketIconRenderer.renderIcon(server, x, listWidth, y, mouseX,
            // mouseY, mc, owner);
            // Load this.server (ServerData)
            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
            mv.visitFieldInsn(Opcodes.GETFIELD, className, serverFieldName, "L" + serverDataClass + ";");

            // Load parameters: x (slot 2), listWidth (slot 4), y (slot 3), mouseX (slot 6),
            // mouseY (slot 7)
            mv.visitVarInsn(Opcodes.ILOAD, 2); // x
            mv.visitVarInsn(Opcodes.ILOAD, 4); // listWidth
            mv.visitVarInsn(Opcodes.ILOAD, 3); // y
            mv.visitVarInsn(Opcodes.ILOAD, 6); // mouseX
            mv.visitVarInsn(Opcodes.ILOAD, 7); // mouseY

            // Load this.mc (Minecraft)
            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
            mv.visitFieldInsn(Opcodes.GETFIELD, className, mcFieldName, "L" + minecraftClass + ";");

            // Load this.owner (GuiMultiplayer as Object)
            mv.visitVarInsn(Opcodes.ALOAD, 0); // this
            mv.visitFieldInsn(Opcodes.GETFIELD, className, ownerFieldName, "L" + guiMultiplayerClass + ";");

            // Call WebSocketIconRenderer.renderIcon (void return)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "com/aqualantic/socks/hooks/WebSocketIconRenderer",
                    "renderIcon",
                    "(L" + serverDataClass + ";IIIIL" + minecraftClass + ";Ljava/lang/Object;)V",
                    false);
            // No need to handle return value - tooltip is set internally via reflection
        }
    }
}
