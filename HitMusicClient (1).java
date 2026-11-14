package com.hitmusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
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
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() && Config.hitEnabled && entity instanceof LivingEntity) {
                Audio.playHit();
            }
            return ActionResult.PASS;
        });

        HudRenderCallback.EVENT.register(HitMusicClient::renderHud);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int y = 4;
        int n = 0;
        for (Finder.Entry e : Finder.results) {
            if (n++ >= 6) break;
            String s = (e.spawner ? "Spawner " : "Chest ")
                    + e.pos.getX() + " " + e.pos.getY() + " " + e.pos.getZ()
                    + "  " + Math.round(e.dist) + "m " + e.dir;
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), 4, y, e.spawner ? 0xFFFF5555 : 0xFFFFAA00);
            y += 10;
        }
        if (AutoMine.running) {
            ctx.drawTextWithShadow(mc.textRenderer,
                    Text.literal("Auto Mine: " + AutoMine.remaining + " blok tersisa"), 4, y, 0xFF55FF55);
        }
    }
}
