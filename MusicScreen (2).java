package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class MusicScreen extends Screen {
    private static int tab = 0;

    private List<String[]> hits;
    private List<String[]> songs;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int cy;

    public MusicScreen() {
        super(Text.literal("Polino Client"));
    }

    private ButtonWidget add(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
                .dimensions(x, y, w, 18).build());
    }

    private static String onoff(boolean b) {
        return b ? "ON" : "OFF";
    }

    private void toggle(String label, boolean value, int x, int y, int w, Runnable flip) {
        add(label + ": " + onoff(value), x, y, w, () -> {
            flip.run();
            Config.save();
            clearAndInit();
        });
    }

    private void cycle(String label, String value, int x, int y, int w, Runnable next) {
        add(label + ": " + value, x, y, w, () -> {
            next.run();
            Config.save();
            clearAndInit();
        });
    }

    @Override
    protected void init() {
        hits = Audio.hits();
        songs = Audio.songs();
        pw = Math.min(240, this.width - 8);
        ph = Math.min(190, this.height - 8);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        cy = py + 42;

        String[] names = {"Hit", "Musik", "Radar", "Mine", "HUD"};
        int tw = (pw - 8 - 4 * 2) / 5;
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            String label = (tab == i ? "\u00a7e\u00a7n" : "") + names[i];
            add(label, px + 4 + i * (tw + 2), py + 18, tw, () -> {
                tab = idx;
                clearAndInit();
            });
        }

        int x = px + 6;
        int w = pw - 12;
        int half = (w - 4) / 2;
        int third = (w - 8) / 3;

        if (tab == 0) {
            toggle("Suara hit", Config.hitEnabled, x, cy, w, () -> Config.hitEnabled = !Config.hitEnabled);
            cycle("Jenis hit", hits.get(Math.floorMod(Config.hitIndex, hits.size()))[0], x, cy + 22, w,
                    () -> Config.hitIndex = Math.floorMod(Config.hitIndex + 1, hits.size()));
            cycle("Volume hit", Config.hitVol + "%", x, cy + 44, w, () -> Config.hitVol = Config.nextVol(Config.hitVol));
            add("Tes suara hit", x, cy + 66, w, () -> Audio.playHit());
        } else if (tab == 1) {
            String name = songs.get(Math.floorMod(Config.songIndex, songs.size()))[0];
            add("<", x, cy, 20, () -> {
                Config.songIndex = Math.floorMod(Config.songIndex - 1, songs.size());
                Config.save();
                clearAndInit();
            });
            add(name, x + 22, cy, w - 44, () -> {
                Config.songIndex = Math.floorMod(Config.songIndex + 1, songs.size());
                Config.save();
                clearAndInit();
            });
            add(">", x + w - 20, cy, 20, () -> {
                Config.songIndex = Math.floorMod(Config.songIndex + 1, songs.size());
                Config.save();
                clearAndInit();
            });
            add("Musik: " + onoff(Audio.isPlaying()), x, cy + 22, w, () -> {
                if (Audio.isPlaying()) Audio.stopSong();
                else Audio.playSong();
                clearAndInit();
            });
            cycle("Volume lagu", Config.songVol + "%", x, cy + 44, w, () -> Config.songVol = Config.nextVol(Config.songVol));
        } else if (tab == 2) {
            toggle("Spawner", Config.showSpawner, x, cy, third, () -> Config.showSpawner = !Config.showSpawner);
            toggle("Chest", Config.showChest, x + third + 4, cy, third, () -> Config.showChest = !Config.showChest);
            toggle("Tembus", Config.espBox, x + 2 * (third + 4), cy, third, () -> Config.espBox = !Config.espBox);
            toggle("Garis", Config.tracer, x, cy + 22, half, () -> Config.tracer = !Config.tracer);
            toggle("Daftar", Config.showList, x + half + 4, cy + 22, half, () -> Config.showList = !Config.showList);
        } else if (tab == 3) {
            add("Set Pos1", x, cy, third, () -> setPos(1));
            add("Set Pos2", x + third + 4, cy, third, () -> setPos(2));
            add("Hapus", x + 2 * (third + 4), cy, third, () -> {
                Config.pos1 = null;
                Config.pos2 = null;
                Config.save();
                clearAndInit();
            });
            cycle("Kecepatan", AutoMine.SPEEDS[Config.mineSpeed], x, cy + 54, half,
                    () -> Config.mineSpeed = (Config.mineSpeed + 1) % 3);
            cycle("Gerak", AutoMine.SMOOTHS[Config.mineSmooth], x + half + 4, cy + 54, half,
                    () -> Config.mineSmooth = (Config.mineSmooth + 1) % 3);
            toggle("Acak", Config.mineJitter, x, cy + 76, half, () -> Config.mineJitter = !Config.mineJitter);
            toggle("Auto jalan", Config.autoWalk, x + half + 4, cy + 76, half, () -> Config.autoWalk = !Config.autoWalk);
            toggle("Panic /spawn", Config.panic, x, cy + 98, half, () -> Config.panic = !Config.panic);
            cycle("Radius", AutoMine.RADII[Config.panicRadius] + "m", x + half + 4, cy + 98, half,
                    () -> Config.panicRadius = (Config.panicRadius + 1) % AutoMine.RADII.length);
            add("Auto Mine: " + onoff(AutoMine.running), x, cy + 120, w, () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (AutoMine.running) {
                    AutoMine.stop(mc, "Auto Mine dihentikan");
                    clearAndInit();
                } else {
                    AutoMine.start(mc);
                    if (AutoMine.running) this.close();
                }
            });
        } else {
            toggle("Koordinat", Config.showCoords, x, cy, half, () -> Config.showCoords = !Config.showCoords);
            toggle("FPS", Config.showFps, x + half + 4, cy, half, () -> Config.showFps = !Config.showFps);
            toggle("Auto sprint", Config.autoSprint, x, cy + 22, half, () -> Config.autoSprint = !Config.autoSprint);
            toggle("Watermark", Config.watermark, x + half + 4, cy + 22, half, () -> Config.watermark = !Config.watermark);
            add("Set Base", x, cy + 44, half, () -> setBase(true));
            add("Hapus Base", x + half + 4, cy + 44, half, () -> setBase(false));
        }
    }

    private void setPos(int n) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        BlockPos p = mc.player.getBlockPos();
        if (n == 1) Config.pos1 = p;
        else Config.pos2 = p;
        Config.save();
        clearAndInit();
    }

    private void setBase(boolean set) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (set && mc.player != null) Config.base = mc.player.getBlockPos();
        else if (!set) Config.base = null;
        Config.save();
        clearAndInit();
    }

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x90000000);
        context.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF3A7BFF);
        context.fill(px, py, px + pw, py + ph, 0xF0101018);
        context.fill(px, py + 15, px + pw, py + 16, 0xFF3A7BFF);
    }

    private void line(DrawContext context, String s, int y, int color) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(s), px + 8, y, color);
    }

    private static String fmt(BlockPos p) {
        return p == null ? "belum diset" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u00a7b\u00a7lPolino \u00a7f\u00a7lClient"), this.width / 2, py + 4, 0xFFFFFFFF);
        if (tab == 1) {
            line(context, "Status: " + (Audio.isPlaying() ? "memutar" : "berhenti"), cy + 70, 0xFFAAAAAA);
            line(context, "Volume baru berlaku di lagu berikutnya", cy + 82, 0xFF777777);
        } else if (tab == 2) {
            line(context, "Terdeteksi: " + Finder.spawnerCount + " spawner, " + Finder.chestCount + " chest",
                    cy + 46, 0xFFAAAAAA);
            line(context, "Merah = spawner, Oranye = chest", cy + 58, 0xFFAAAAAA);
            line(context, "Hanya chunk yang sudah ke-load", cy + 70, 0xFF777777);
        } else if (tab == 3) {
            line(context, "Pos1: " + fmt(Config.pos1), cy + 22, 0xFFAAAAAA);
            line(context, "Pos2: " + fmt(Config.pos2), cy + 32, 0xFFAAAAAA);
            line(context, "Ukuran: " + AutoMine.volume() + " blok (pegang pickaxe)", cy + 42, 0xFFAAAAAA);
        } else if (tab == 4) {
            line(context, "Base: " + fmt(Config.base), cy + 66, 0xFFAAAAAA);
        }
    }

    @Override
    public void removed() {
        Config.save();
    }
}
