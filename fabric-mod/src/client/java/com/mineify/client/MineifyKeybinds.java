package com.mineify.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class MineifyKeybinds {
    private static final KeyBinding.Category MINEIFY_CATEGORY =
            KeyBinding.Category.create(Identifier.of("mineify", "mineify"));

    private static KeyBinding openGuiKey;

    public static void register() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mineify.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                MINEIFY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.player != null) {
                    MinecraftClient.getInstance().setScreen(new MineifyScreen());
                }
            }
        });
    }
}
