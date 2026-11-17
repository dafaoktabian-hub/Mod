package com.hitmusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class HitMusicClient implements ClientModInitializer {
    public static KeyBinding openKey;
    public static KeyBinding freecamKey;
    public static KeyBinding fullbrightKey;
    private static boolean pendingOpen = false;

    @Override
    public void onInitializeClient() {
        Config.load();
        Waypoints.load();
        Modules.init();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("hitmusic", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hitmusic.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, category));
        freecamKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("Polino Freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        fullbrightKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("Polino Fullbright", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("hitmusic").executes(ctx -> {
                    pendingOpen = true;
                    return 1;
                })));

        ClientTickEvents.START_CLIENT_TICK.register(client -> Modules.startTick(client));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                pendingOpen = true;
            }
            while (freecamKey.wasPressed()) {
                Feature f = Modules.get("freecam");
                if (f != null) f.setOn(!f.isOn(), client);
            }
            while (fullbrightKey.wasPressed()) {
                Feature f = Modules.get("fullbright");
                if (f != null) f.setOn(!f.isOn(), client);
            }
            if (pendingOpen && client.currentScreen == null && client.player != null) {
                pendingOpen = false;
                client.setScreen(new MusicScreen());
            }
            Finder.tick(client);
            AutoMine.tick(client);
            Modules.tick(client);
            if (Config.getBool("survival.respawn", false) && client.player != null
                    && client.currentScreen instanceof DeathScreen) {
                client.player.requestRespawn();
            }
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
