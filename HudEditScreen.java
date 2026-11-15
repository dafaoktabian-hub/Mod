package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class HudEditScreen extends Screen {
    private static final int[] STEPS = {1, 4, 10, 25};
    private static int sel = 0;
    private static int stepIdx = 1;

    private final Screen parent;

    public HudEditScreen(Screen parent) {
        super(Text.literal("Edit HUD"));
        this.parent = parent;
    }

    private void add(String label, int x, int y, int w, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
                .dimensions(x, y, w, 18).build());
    }

    private HudLayout.El cur() {
        sel = Math.floorMod(sel, HudLayout.ELS.size());
        return HudLayout.ELS.get(sel);
    }

    private void nudge(int dx, int dy) {
        int step = STEPS[stepIdx];
        MinecraftClient mc = MinecraftClient.getInstance();
        HudLayout.move(cur(), dx * step, dy * step, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        Config.save();
    }

    @Override
    protected void init() {
        int pw = Math.min(320, this.width - 8);
        int px = (this.width - pw) / 2;
        int py = this.height - 84;
        int x = px + 4;
        int w = pw - 8;
        int y = py + 4;
        HudLayout.El e = cur();

        add("<", x, y, 20, () -> {
            sel = Math.floorMod(sel - 1, HudLayout.ELS.size());
            clearAndInit();
        });
        add(e.name, x + 22, y, w - 22 - 22 - 66, () -> {
            sel = Math.floorMod(sel + 1, HudLayout.ELS.size());
            clearAndInit();
        });
        add(">", x + w - 66 - 22, y, 20, () -> {
            sel = Math.floorMod(sel + 1, HudLayout.ELS.size());
            clearAndInit();
        });
        add(HudLayout.on(e) ? "\u00a7aON" : "\u00a7cOFF", x + w - 64, y, 64, () -> {
            HudLayout.setOn(cur(), !HudLayout.on(cur()));
            Config.save();
            clearAndInit();
        });

        int q = (w - 6) / 4;
        add("Kiri", x, y + 22, q, () -> nudge(-1, 0));
        add("Kanan", x + (q + 2), y + 22, q, () -> nudge(1, 0));
        add("Atas", x + 2 * (q + 2), y + 22, q, () -> nudge(0, -1));
        add("Bawah", x + 3 * (q + 2), y + 22, q, () -> nudge(0, 1));

        int t = (w - 4) / 3;
        add("Langkah: " + STEPS[stepIdx] + "px", x, y + 44, t, () -> {
            stepIdx = (stepIdx + 1) % STEPS.length;
            clearAndInit();
        });
        add("Reset HUD", x + (t + 2), y + 44, t, () -> {
            HudLayout.resetAll();
            Config.save();
            clearAndInit();
        });
        add("Selesai", x + 2 * (t + 2), y + 44, t, () -> this.close());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            HudLayout.drawAll(context, mc, mc.player, true);
        }
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        HudLayout.El e = cur();
        int ex = HudLayout.x(e, sw, sh);
        int ey = HudLayout.y(e, sw, sh);
        double t = System.nanoTime() / 1.0e9;
        int a = 150 + (int) (90.0 * Math.sin(t * 4.0));
        int color = (Math.max(0, Math.min(255, a)) << 24) | 0x3A9BFF;
        int x1 = ex - 2;
        int y1 = ey - 2;
        int x2 = ex + e.w + 2;
        int y2 = ey + e.h + 1;
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2, x2, y2 + 1, color);
        context.fill(x1, y1, x1 + 1, y2 + 1, color);
        context.fill(x2, y1, x2 + 1, y2 + 1, color);
        String hint = "Pilih elemen lalu geser pakai tombol";
        int hw = this.textRenderer.getWidth(hint);
        context.drawTextWithShadow(this.textRenderer, Text.literal(hint), (this.width - hw) / 2, this.height - 98, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        Config.save();
        MinecraftClient.getInstance().setScreen(parent);
    }
}
