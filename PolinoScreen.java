package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class PolinoScreen extends Screen {
    private static final int ACCENT = 0xFF3A9BFF;
    private static final String[] TABS = {"Hit", "Musik", "Radar", "Mine", "Area", "HUD"};

    private static int tab = 0;

    private List<String[]> hits;
    private List<String[]> songs;
    private final List<String> labels = new ArrayList<>();
    private final List<Integer> labelY = new ArrayList<>();

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int x0;
    private int cw;
    private int rowY;
    private int infoY;

    private static final int SIDE_W = 56;
    private static final int BTN_W = 70;

    public PolinoScreen() {
        super(Text.literal("Polino Client"));
    }

    private ButtonWidget add(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run())
                .dimensions(x, y, w, 18).build());
    }

    private void row(String label, String value, Runnable action) {
        labels.add(label);
        labelY.add(rowY);
        add(value, x0 + cw - BTN_W, rowY, BTN_W, action);
        rowY += 22;
        infoY = rowY + 2;
    }

    private void rowToggle(String label, boolean on, Runnable flip) {
        row(label, on ? "\u00a7aON" : "\u00a7cOFF", () -> {
            flip.run();
            PolinoConfig.save();
            clearAndInit();
        });
    }

    private void rowCycle(String label, String value, Runnable next) {
        row(label, "\u00a7b" + value, () -> {
            next.run();
            PolinoConfig.save();
            clearAndInit();
        });
    }

    @Override
    protected void init() {
        labels.clear();
        labelY.clear();
        hits = PolinoAudio.hits();
        songs = PolinoAudio.songs();

        pw = Math.min(270, this.width - 8);
        ph = Math.min(188, this.height - 8);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        x0 = px + SIDE_W + 10;
        cw = pw - SIDE_W - 16;
        rowY = py + 24;
        infoY = rowY;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            String label = (tab == i ? "\u00a7e\u00a7l" : "") + TABS[i];
            add(label, px + 5, py + 24 + i * 22, SIDE_W - 2, () -> {
                tab = idx;
                clearAndInit();
            });
        }

        if (tab == 0) {
            rowToggle("Suara Hit", PolinoConfig.hitEnabled, () -> PolinoConfig.hitEnabled = !PolinoConfig.hitEnabled);
            rowCycle("Jenis Suara", hits.get(Math.floorMod(PolinoConfig.hitIndex, hits.size()))[0],
                    () -> PolinoConfig.hitIndex = Math.floorMod(PolinoConfig.hitIndex + 1, hits.size()));
            rowCycle("Volume", PolinoConfig.hitVol + "%", () -> PolinoConfig.hitVol = PolinoConfig.nextVol(PolinoConfig.hitVol));
            row("Tes Suara", "Putar", () -> PolinoAudio.playHit());
        } else if (tab == 1) {
            rowCycle("Lagu", songs.get(Math.floorMod(PolinoConfig.songIndex, songs.size()))[0],
                    () -> PolinoConfig.songIndex = Math.floorMod(PolinoConfig.songIndex + 1, songs.size()));
            row("Lagu Sebelumnya", "<<", () -> {
                PolinoConfig.songIndex = Math.floorMod(PolinoConfig.songIndex - 1, songs.size());
                PolinoConfig.save();
                clearAndInit();
            });
            rowToggle("Musik", PolinoAudio.isPlaying(), () -> {
                if (PolinoAudio.isPlaying()) PolinoAudio.stopSong();
                else PolinoAudio.playSong();
            });
            rowCycle("Volume", PolinoConfig.songVol + "%", () -> PolinoConfig.songVol = PolinoConfig.nextVol(PolinoConfig.songVol));
        } else if (tab == 2) {
            rowToggle("Spawner ESP", PolinoConfig.showSpawner, () -> PolinoConfig.showSpawner = !PolinoConfig.showSpawner);
            rowToggle("Chest ESP", PolinoConfig.showChest, () -> PolinoConfig.showChest = !PolinoConfig.showChest);
            rowToggle("Kotak Tembus", PolinoConfig.espBox, () -> PolinoConfig.espBox = !PolinoConfig.espBox);
            rowToggle("Garis Arah", PolinoConfig.tracer, () -> PolinoConfig.tracer = !PolinoConfig.tracer);
            rowToggle("Daftar di HUD", PolinoConfig.showList, () -> PolinoConfig.showList = !PolinoConfig.showList);
        } else if (tab == 3) {
            row("Auto Mine", PolinoMine.running ? "\u00a7aON" : "\u00a7cOFF", () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (PolinoMine.running) {
                    PolinoMine.stop(mc, "Auto Mine dihentikan");
                    clearAndInit();
                } else {
                    PolinoMine.start(mc);
                    if (PolinoMine.running) this.close();
                }
            });
            rowCycle("Kecepatan", PolinoMine.SPEEDS[PolinoConfig.mineSpeed],
                    () -> PolinoConfig.mineSpeed = (PolinoConfig.mineSpeed + 1) % 3);
            rowCycle("Gerakan", PolinoMine.SMOOTHS[PolinoConfig.mineSmooth],
                    () -> PolinoConfig.mineSmooth = (PolinoConfig.mineSmooth + 1) % 3);
            rowToggle("Acak Manusia", PolinoConfig.mineJitter, () -> PolinoConfig.mineJitter = !PolinoConfig.mineJitter);
            rowToggle("Auto Jalan", PolinoConfig.autoWalk, () -> PolinoConfig.autoWalk = !PolinoConfig.autoWalk);
        } else if (tab == 4) {
            row("Posisi 1", "Set", () -> setPos(1));
            row("Posisi 2", "Set", () -> setPos(2));
            row("Hapus Area", "Hapus", () -> {
                PolinoConfig.pos1 = null;
                PolinoConfig.pos2 = null;
                PolinoConfig.save();
                clearAndInit();
            });
            rowToggle("Panic /spawn", PolinoConfig.panic, () -> PolinoConfig.panic = !PolinoConfig.panic);
            rowCycle("Radius Panic", PolinoMine.RADII[PolinoConfig.panicRadius] + "m",
                    () -> PolinoConfig.panicRadius = (PolinoConfig.panicRadius + 1) % PolinoMine.RADII.length);
        } else {
            rowToggle("Koordinat", PolinoConfig.showCoords, () -> PolinoConfig.showCoords = !PolinoConfig.showCoords);
            rowToggle("FPS", PolinoConfig.showFps, () -> PolinoConfig.showFps = !PolinoConfig.showFps);
            rowToggle("Auto Sprint", PolinoConfig.autoSprint, () -> PolinoConfig.autoSprint = !PolinoConfig.autoSprint);
            rowToggle("Watermark", PolinoConfig.watermark, () -> PolinoConfig.watermark = !PolinoConfig.watermark);
            row("Set Base", "Set", () -> setBase(true));
            row("Hapus Base", "Hapus", () -> setBase(false));
        }
    }

    private void setPos(int n) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        BlockPos p = mc.player.getBlockPos();
        if (n == 1) PolinoConfig.pos1 = p;
        else PolinoConfig.pos2 = p;
        PolinoConfig.save();
        clearAndInit();
    }

    private void setBase(boolean set) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (set && mc.player != null) PolinoConfig.base = mc.player.getBlockPos();
        else if (!set) PolinoConfig.base = null;
        PolinoConfig.save();
        clearAndInit();
    }

    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x90000000);
        context.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, ACCENT);
        context.fill(px, py, px + pw, py + ph, 0xF0101018);
        context.fill(px, py, px + pw, py + 18, 0xFF0A0A12);
        context.fill(px, py + 18, px + pw, py + 19, ACCENT);
        context.fill(px, py + 19, px + SIDE_W + 7, py + ph, 0xFF0A0A12);
        context.fill(px + 1, py + 24 + tab * 22, px + 3, py + 24 + tab * 22 + 18, ACCENT);
        for (int y : labelY) {
            context.fill(x0 - 3, y - 2, x0 + cw, y + 20, 0x30FFFFFF);
        }
    }

    private void info(DrawContext context, String s, int y, int color) {
        context.drawTextWithShadow(this.textRenderer, Text.literal(s), x0 + 2, y, color);
    }

    private static String fmt(BlockPos p) {
        return p == null ? "belum diset" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("\u00a7b\u00a7lPolino \u00a7f\u00a7lClient"), px + 8, py + 5, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("v2"), px + pw - 18, py + 5, 0xFF888888);
        for (int i = 0; i < labels.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(labels.get(i)),
                    x0 + 2, labelY.get(i) + 5, 0xFFFFFFFF);
        }
        if (tab == 1) {
            info(context, "Status: " + (PolinoAudio.isPlaying() ? "memutar" : "berhenti"), infoY, 0xFFAAAAAA);
        } else if (tab == 2) {
            info(context, PolinoFinder.spawnerCount + " spawner, " + PolinoFinder.chestCount + " chest terdeteksi",
                    infoY, 0xFFAAAAAA);
            info(context, "Merah = spawner, Oranye = chest", infoY + 10, 0xFF777777);
        } else if (tab == 3) {
            info(context, "Pegang pickaxe. Atur area di tab Area.", infoY, 0xFF777777);
        } else if (tab == 4) {
            info(context, "P1: " + fmt(PolinoConfig.pos1) + "   P2: " + fmt(PolinoConfig.pos2), infoY, 0xFFAAAAAA);
            info(context, "Ukuran: " + PolinoMine.volume() + " blok", infoY + 10, 0xFFAAAAAA);
        } else if (tab == 5) {
            info(context, "Base: " + fmt(PolinoConfig.base), infoY, 0xFFAAAAAA);
        }
    }

    @Override
    public void removed() {
        PolinoConfig.save();
    }
}
