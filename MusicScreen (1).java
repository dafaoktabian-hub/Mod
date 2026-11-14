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
        super(Text.literal("Hit & Music"));
    }

    private ButtonWidget add(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
                .dimensions(x, y, w, 18).build());
    }

    private static String onoff(boolean b) {
        return b ? "ON" : "OFF";
    }

    @Override
    protected void init() {
        hits = Audio.hits();
        songs = Audio.songs();
        pw = Math.min(230, this.width - 8);
        ph = Math.min(182, this.height - 8);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        cy = py + 30;

        int tw = (pw - 12) / 3;
        String[] names = {"Hit", "Musik", "Fitur"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            String label = (tab == i ? "\u00a7e\u00a7n" : "") + names[i];
            add(label, px + 4 + i * (tw + 2), py + 4, tw, () -> {
                tab = idx;
                clearAndInit();
            });
        }

        int x = px + 6;
        int w = pw - 12;
        int half = (w - 4) / 2;

        if (tab == 0) {
            add("Suara hit: " + onoff(Config.hitEnabled), x, cy, w, () -> {
                Config.hitEnabled = !Config.hitEnabled;
                Config.save();
                clearAndInit();
            });
            add("Jenis hit: " + hits.get(Math.floorMod(Config.hitIndex, hits.size()))[0], x, cy + 22, w, () -> {
                Config.hitIndex = Math.floorMod(Config.hitIndex + 1, hits.size());
                Config.save();
                clearAndInit();
            });
            add("Volume hit: " + Config.hitVol + "%", x, cy + 44, w, () -> {
                Config.hitVol = Config.nextVol(Config.hitVol);
                Config.save();
                clearAndInit();
            });
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
            add("Volume lagu: " + Config.songVol + "%", x, cy + 44, w, () -> {
                Config.songVol = Config.nextVol(Config.songVol);
                Config.save();
                clearAndInit();
            });
        } else {
            add("Spawner: " + onoff(Config.showSpawner), x, cy, half, () -> {
                Config.showSpawner = !Config.showSpawner;
                Config.save();
                clearAndInit();
            });
            add("Chest: " + onoff(Config.showChest), x + half + 4, cy, half, () -> {
                Config.showChest = !Config.showChest;
                Config.save();
                clearAndInit();
            });
            add("Set Pos1", x, cy + 48, half, () -> setPos(1));
            add("Set Pos2", x + half + 4, cy + 48, half, () -> setPos(2));
            add("Auto jalan: " + onoff(Config.autoWalk), x, cy + 104, half, () -> {
                Config.autoWalk = !Config.autoWalk;
                Config.save();
                clearAndInit();
            });
            add("Hapus pos", x + half + 4, cy + 104, half, () -> {
                Config.pos1 = null;
                Config.pos2 = null;
                Config.save();
                clearAndInit();
            });
            add("Auto Mine: " + onoff(AutoMine.running), x, cy + 126, w, () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (AutoMine.running) {
                    AutoMine.stop(mc, "Auto Mine dihentikan");
                    clearAndInit();
                } else {
                    AutoMine.start(mc);
                    if (AutoMine.running) this.close();
                }
            });
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

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x90000000);
        context.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF3A7BFF);
        context.fill(px, py, px + pw, py + ph, 0xF0101018);
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
        if (tab == 1) {
            line(context, "Status: " + (Audio.isPlaying() ? "memutar" : "berhenti"), cy + 70, 0xFFAAAAAA);
            line(context, "Volume baru berlaku di lagu berikutnya", cy + 82, 0xFF777777);
        } else if (tab == 2) {
            line(context, "Terdeteksi: " + Finder.spawnerCount + " spawner, " + Finder.chestCount + " chest",
                    cy + 22, 0xFFAAAAAA);
            line(context, "Auto Mine (pegang pickaxe)", cy + 36, 0xFFFFFF55);
            line(context, "Pos1: " + fmt(Config.pos1), cy + 70, 0xFFAAAAAA);
            line(context, "Pos2: " + fmt(Config.pos2), cy + 80, 0xFFAAAAAA);
            line(context, "Ukuran: " + AutoMine.volume() + " blok", cy + 90, 0xFFAAAAAA);
        }
    }

    @Override
    public void removed() {
        Config.save();
    }
}
