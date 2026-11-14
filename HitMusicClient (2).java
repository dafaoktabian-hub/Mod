package com.hitmusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class HitMusicClient implements ClientModInitializer {
    public static KeyBinding openKey;
    private static boolean pendingOpen = false;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("hitmusic", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hitmusic.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("hitmusic").executes(ctx -> {
                    pendingOpen = true;
                    return 1;
                })));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                pendingOpen = true;
            }
            if (pendingOpen && client.currentScreen == null && client.player != null) {
                pendingOpen = false;
                client.setScreen(new MusicScreen());
            }
            Finder.tick(client);
            AutoMine.tick(client);
            if (Config.autoSprint && client.player != null && client.currentScreen == null
                    && client.options.forwardKey.isPressed() && !client.player.isSneaking()
                    && client.player.getHungerManager().getFoodLevel() > 6) {
                client.player.setSprinting(true);
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && Config.hitEnabled && entity instanceof LivingEntity) {
                Audio.playHit();
            }
            return ActionResult.PASS;
        });

        HudRenderCallback.EVENT.register(Hud::render);
    }
}
