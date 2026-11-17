package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MusicScreen extends Screen {
    private static final int[] THEMES = {0x3A9BFF, 0xA06BFF, 0xFF5A5A, 0x35D36B, 0xFFA030};
    private static final String[] THEME_NAMES = {"Biru", "Ungu", "Merah", "Hijau", "Oranye"};
    private static final String[] TABS = {"Hit", "Musik", "Radar", "Mine", "Ore", "Area", "Visual", "Surv", "Titik", "HUD"};
    private static final int[] TREE_RADII = {8, 12, 16, 24};

    private static int tab = 0;
    private static final Map<String, Float> TOGGLE = new HashMap<>();
    private static final Map<String, Float> HOVER = new HashMap<>();
    private static long lastNano = 0L;
    private static long openedAt = 0L;
    private static long tabAt = 0L;
    private static float indicatorX = 0.0f;

    private static final class Card {
        String label;
        String key;
        int type;
        boolean on;
        String value;
        int x;
        int y;
        int w;
        int h;
    }

    private final List<Card> cards = new ArrayList<>();
    private List<String[]> hits;
    private List<String[]> songs;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int tabW;
    private int colW;

    private static final int CARD_H = 32;

    public MusicScreen() {
        super(Text.literal("Polino Client"));
        openedAt = System.nanoTime();
        tabAt = openedAt;
        indicatorX = 0.0f;
    }

    private static int accent() {
        return THEMES[Math.floorMod(Config.getInt("gui.theme", 0), THEMES.length)];
    }

    private ButtonWidget invisible(int x, int y, int w, int h, Runnable action) {
        ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> action.run())
                .dimensions(x, y, w, h).build());
        b.setAlpha(0.0f);
        return b;
    }

    private void addCard(int type, String label, boolean on, String value, Runnable action) {
        int idx = cards.size();
        Card c = new Card();
        c.label = label;
        c.key = tab + ":" + label;
        c.type = type;
        c.on = on;
        c.value = value;
        c.w = colW;
        c.h = CARD_H;
        c.x = px + 6 + (idx % 2) * (colW + 6);
        c.y = py + 54 + (idx / 2) * (CARD_H + 5);
        cards.add(c);
        invisible(c.x, c.y, c.w, c.h, action);
        if (type == 0 && !TOGGLE.containsKey(c.key)) {
            TOGGLE.put(c.key, on ? 1.0f : 0.0f);
        }
    }

    private void cardToggle(String label, boolean on, Runnable flip) {
        addCard(0, label, on, "", () -> {
            flip.run();
            Config.save();
            clearAndInit();
        });
    }

    private void cardCycle(String label, String value, Runnable next) {
        addCard(1, label, false, value, () -> {
            next.run();
            Config.save();
            clearAndInit();
        });
    }

    private void cardAction(String label, String value, Runnable action) {
        addCard(2, label, false, value, action);
    }

    @Override
    protected void init() {
        cards.clear();
        hits = Audio.hits();
        songs = Audio.songs();
        pw = Math.min(400, this.width - 8);
        ph = Math.min(262, this.height - 8);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        tabW = (pw - 12) / TABS.length;
        colW = (pw - 18) / 2;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            invisible(px + 6 + i * tabW, py + 28, tabW, 20, () -> {
                if (tab != idx) {
                    tab = idx;
                    tabAt = System.nanoTime();
                }
                clearAndInit();
            });
        }

        switch (tab) {
            case 0:
                buildHit();
                break;
            case 1:
                buildMusic();
                break;
            case 2:
                buildRadar();
                break;
            case 3:
                buildMine();
                break;
            case 4:
                buildOre();
                break;
            case 5:
                buildArea();
                break;
            case 6:
                buildVisual();
                break;
            case 7:
                buildSurvival();
                break;
            case 8:
                buildWaypoint();
                break;
            default:
                buildHud();
                break;
        }
    }

    private void buildHit() {
        cardToggle("Suara Hit", Config.hitEnabled, () -> Config.hitEnabled = !Config.hitEnabled);
        cardCycle("Jenis Suara", hits.get(Math.floorMod(Config.hitIndex, hits.size()))[0],
                () -> Config.hitIndex = Math.floorMod(Config.hitIndex + 1, hits.size()));
        cardCycle("Volume Hit", Config.hitVol + "%", () -> Config.hitVol = Config.nextVol(Config.hitVol));
        cardAction("Tes Suara", "Putar", () -> Audio.playHit());
    }

    private void buildMusic() {
        cardCycle("Lagu", songs.get(Math.floorMod(Config.songIndex, songs.size()))[0],
                () -> Config.songIndex = Math.floorMod(Config.songIndex + 1, songs.size()));
        cardAction("Lagu Sebelumnya", "<<", () -> {
            Config.songIndex = Math.floorMod(Config.songIndex - 1, songs.size());
            Config.save();
            clearAndInit();
        });
        cardToggle("Musik", Audio.isPlaying(), () -> {
            if (Audio.isPlaying()) Audio.stopSong();
            else Audio.playSong();
        });
        cardCycle("Volume Lagu", Config.songVol + "%", () -> Config.songVol = Config.nextVol(Config.songVol));
    }

    private void buildRadar() {
        cardToggle("Spawner ESP", Config.showSpawner, () -> Config.showSpawner = !Config.showSpawner);
        cardToggle("Chest ESP", Config.showChest, () -> Config.showChest = !Config.showChest);
        cardToggle("Kotak Tembus", Config.espBox, () -> Config.espBox = !Config.espBox);
        cardToggle("Garis Arah", Config.tracer, () -> Config.tracer = !Config.tracer);
        cardToggle("Daftar di HUD", Config.showList, () -> Config.showList = !Config.showList);
    }

    private void buildMine() {
        addCard(0, "Auto Mine", AutoMine.running && !AutoMine.oreSearch, "", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (AutoMine.running) {
                AutoMine.stop(mc, "Auto Mine dihentikan");
                clearAndInit();
            } else {
                AutoMine.start(mc, false);
                if (AutoMine.running) this.close();
            }
        });
        cardCycle("Kecepatan", AutoMine.SPEEDS[Config.mineSpeed], () -> Config.mineSpeed = (Config.mineSpeed + 1) % 3);
        cardCycle("Gerakan", AutoMine.SMOOTHS[Config.mineSmooth], () -> Config.mineSmooth = (Config.mineSmooth + 1) % 3);
        cardToggle("Acak Manusia", Config.mineJitter, () -> Config.mineJitter = !Config.mineJitter);
        cardToggle("Auto Jalan", Config.autoWalk, () -> Config.autoWalk = !Config.autoWalk);
        cardToggle("Pickaxe 3x3", Config.pick3, () -> Config.pick3 = !Config.pick3);
    }

    private void buildOre() {
        addCard(0, "Cari Ore", AutoMine.running && AutoMine.oreSearch, "", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (AutoMine.running) {
                AutoMine.stop(mc, "Cari Ore dihentikan");
                clearAndInit();
            } else {
                AutoMine.start(mc, true);
                if (AutoMine.running) this.close();
            }
        });
        cardCycle("Target Ore", AutoMine.ORES[AutoMine.oreMode()],
                () -> Config.setInt("mine.ore", (AutoMine.oreMode() + 1) % AutoMine.ORES.length));
        cardToggle("Ore Tertutup", AutoMine.oreHidden(), () -> Config.setBool("mine.hidden", !AutoMine.oreHidden()));
    }

    private void buildArea() {
        cardAction("Posisi 1", "Set", () -> setPos(1));
        cardAction("Posisi 2", "Set", () -> setPos(2));
        cardAction("Hapus Area", "Hapus", () -> {
            Config.pos1 = null;
            Config.pos2 = null;
            Config.save();
            clearAndInit();
        });
        cardToggle("Panic /spawn", Config.panic, () -> Config.panic = !Config.panic);
        cardCycle("Radius Panic", AutoMine.RADII[Config.panicRadius] + "m",
                () -> Config.panicRadius = (Config.panicRadius + 1) % AutoMine.RADII.length);
    }

    private void buildVisual() {
        Feature fb = Modules.get("fullbright");
        if (fb == null) {
            cardAction("Fullbright", "N/A", () -> {
            });
        } else {
            cardToggle("Fullbright", fb.isOn(), () -> fb.setOn(!fb.isOn(), MinecraftClient.getInstance()));
        }
        Feature fc = Modules.get("freecam");
        if (fc == null) {
            cardAction("Freecam", "N/A", () -> {
            });
        } else {
            addCard(0, "Freecam", fc.isOn(), "", () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                fc.setOn(!fc.isOn(), mc);
                if (fc.isOn()) this.close();
                else clearAndInit();
            });
        }
        cardCycle("Speed Freecam", "x" + Config.getInt("freecam.speed", 2),
                () -> Config.setInt("freecam.speed", Config.getInt("freecam.speed", 2) % 4 + 1));
        cardToggle("Auto Sprint", Config.autoSprint, () -> Config.autoSprint = !Config.autoSprint);
    }

    private void buildSurvival() {
        Feature tree = Modules.get("autotree");
        if (tree == null) {
            cardAction("Auto Tebang", "N/A", () -> {
            });
        } else {
            addCard(0, "Auto Tebang", tree.isOn(), "", () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                tree.setOn(!tree.isOn(), mc);
                if (tree.isOn()) this.close();
                else clearAndInit();
            });
        }
        cardCycle("Radius Tebang", Config.getInt("tree.radius", 12) + " blok", () -> {
            int cur = Config.getInt("tree.radius", 12);
            int next = TREE_RADII[0];
            for (int i = 0; i < TREE_RADII.length; i++) {
                if (TREE_RADII[i] == cur) {
                    next = TREE_RADII[(i + 1) % TREE_RADII.length];
                    break;
                }
            }
            Config.setInt("tree.radius", next);
        });
        cardToggle("Tanam Ulang", Config.getBool("tree.replant", true),
                () -> Config.setBool("tree.replant", !Config.getBool("tree.replant", true)));
        cardToggle("Auto Respawn", Config.getBool("survival.respawn", false),
                () -> Config.setBool("survival.respawn", !Config.getBool("survival.respawn", false)));
    }

    private void buildWaypoint() {
        Waypoints.WP cur = Waypoints.current();
        cardToggle("Tampil Titik", Waypoints.show, () -> {
            Waypoints.show = !Waypoints.show;
            Waypoints.save();
        });
        cardCycle("Pilih", cur == null ? "-" : cur.name, () -> {
            if (!Waypoints.list.isEmpty()) {
                Waypoints.selected = Math.floorMod(Waypoints.selected + 1, Waypoints.list.size());
            }
        });
        cardAction("Tambah", "Di Sini", () -> {
            Waypoints.addHere(MinecraftClient.getInstance());
            clearAndInit();
        });
        cardAction("Hapus", "Terpilih", () -> {
            Waypoints.removeSelected();
            clearAndInit();
        });
        if (cur == null) {
            cardAction("Aktif", "-", () -> {
            });
        } else {
            cardToggle("Aktif", cur.on, () -> {
                cur.on = !cur.on;
                Waypoints.save();
            });
        }
    }

    private void buildHud() {
        cardAction("Edit HUD", "Buka", () -> MinecraftClient.getInstance().setScreen(new HudEditScreen(this)));
        cardAction("Reset HUD", "Reset", () -> {
            HudLayout.resetAll();
            Config.save();
            clearAndInit();
        });
        cardAction("Set Base", "Set", () -> setBase(true));
        cardAction("Hapus Base", "Hapus", () -> setBase(false));
        cardCycle("Tema GUI", THEME_NAMES[Math.floorMod(Config.getInt("gui.theme", 0), THEMES.length)],
                () -> Config.setInt("gui.theme", (Config.getInt("gui.theme", 0) + 1) % THEMES.length));
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

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static float ease(float t) {
        float u = 1.0f - clamp01(t);
        return 1.0f - u * u * u;
    }

    private static int c(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return (r << 16) | (g << 8) | bl;
    }

    private static String fmt(BlockPos p) {
        return p == null ? "belum diset" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private String infoText() {
        switch (tab) {
            case 0:
                return "Suara bawaan atau custom (hit.ogg di repo)";
            case 1:
                return "Status: " + (Audio.isPlaying() ? "memutar" : "berhenti");
            case 2:
                return Finder.spawnerCount + " spawner, " + Finder.chestCount + " chest terdeteksi";
            case 3:
                return "Pegang pickaxe. Area diatur di tab Area.";
            case 4:
                return "Cari " + AutoMine.ORES[AutoMine.oreMode()] + (AutoMine.oreHidden() ? " (tembus batu)" : " (yang terlihat)")
                        + ". Area di tab Area.";
            case 5:
                return "P1: " + fmt(Config.pos1) + "  P2: " + fmt(Config.pos2) + "  (" + AutoMine.volume() + " blok)";
            case 6:
                return "Freecam: W A S D, Spasi naik, Shift turun, Ctrl cepat";
            case 7:
                return "Kapak di tangan kanan, sapling di tangan kiri";
            case 8: {
                Waypoints.WP w = Waypoints.current();
                if (w == null) return "Belum ada titik";
                return w.name + ": " + w.x + " " + w.y + " " + w.z + "  (" + (Waypoints.selected + 1) + "/" + Waypoints.list.size() + ")";
            }
            default:
                return "Base: " + fmt(Config.base);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int acc = accent();
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0.016f : Math.min(0.05f, (now - lastNano) / 1.0e9f);
        lastNano = now;

        float e = ease((now - openedAt) / 280.0e6f);
        int a = (int) (e * 255.0f);
        int oy = (int) ((1.0f - e) * 18.0f);
        int py2 = py + oy;
        float ct = clamp01((now - tabAt) / 320.0e6f);

        context.fill(px - 1, py2 - 1, px + pw + 1, py2 + ph + 1, c(a, acc));
        context.fill(px, py2, px + pw, py2 + ph, c(Math.min(a, 0xF4), 0x0E0E16));
        context.fill(px, py2, px + pw, py2 + 26, c(a, 0x08080E));
        context.fill(px, py2 + 26, px + pw, py2 + 27, c(a, lerpRgb(0x0E0E16, acc, 0.6f)));
        context.fill(px, py2 + 27, px + pw, py2 + 49, c(a, 0x0A0A12));
        context.fill(px, py2 + ph - 18, px + pw, py2 + ph, c(a, 0x08080E));

        if (e > 0.3f) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("\u00a7l\u00a7bPolino \u00a7f\u00a7lClient"), px + 10, py2 + 9, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("v4"), px + pw - 18, py2 + 9, 0xFF888888);
        }

        float targetX = px + 6 + tab * tabW;
        indicatorX += (targetX - indicatorX) * (1.0f - (float) Math.exp(-dt * 16.0f));
        if (indicatorX < px) indicatorX = targetX;
        int ix = (int) indicatorX;
        context.fill(ix, py2 + 29, ix + tabW, py2 + 47, c((int) (a * 0.22f), acc));
        context.fill(ix, py2 + 45, ix + tabW, py2 + 47, c(a, acc));

        for (int i = 0; i < TABS.length; i++) {
            int tx = px + 6 + i * tabW;
            boolean hv = mouseX >= tx && mouseX <= tx + tabW && mouseY >= py + 28 && mouseY <= py + 48;
            int color = i == tab ? 0xFFFFFFFF : (hv ? 0xFFD0E4FF : 0xFF8FA0B8);
            if (e > 0.3f) {
                int tw = this.textRenderer.getWidth(TABS[i]);
                context.drawTextWithShadow(this.textRenderer, Text.literal(TABS[i]), tx + (tabW - tw) / 2, py2 + 34, color);
            }
        }

        for (int i = 0; i < cards.size(); i++) {
            Card cd = cards.get(i);
            float er = ease(ct * 1.6f - i * 0.09f);
            if (er <= 0.01f) continue;
            int ca = (int) (a * er);
            int yo = (int) ((1.0f - er) * 12.0f);
            int cx = cd.x;
            int cyy = cd.y + oy + yo;

            boolean hv = mouseX >= cd.x && mouseX <= cd.x + cd.w && mouseY >= cd.y && mouseY <= cd.y + cd.h;
            float h = HOVER.containsKey(cd.key) ? HOVER.get(cd.key) : 0.0f;
            h += ((hv ? 1.0f : 0.0f) - h) * (1.0f - (float) Math.exp(-dt * 16.0f));
            HOVER.put(cd.key, h);

            float tp = 0.0f;
            if (cd.type == 0) {
                tp = TOGGLE.containsKey(cd.key) ? TOGGLE.get(cd.key) : (cd.on ? 1.0f : 0.0f);
                tp += ((cd.on ? 1.0f : 0.0f) - tp) * (1.0f - (float) Math.exp(-dt * 14.0f));
                TOGGLE.put(cd.key, tp);
            }

            int bg = lerpRgb(0x181D29, 0x222A3D, h);
            if (cd.type == 0) {
                bg = lerpRgb(bg, lerpRgb(0x181D29, acc, 0.38f), tp);
            }
            context.fill(cx, cyy, cx + cd.w, cyy + cd.h, c(ca, bg));
            int barColor = lerpRgb(0x2A3350, acc, Math.max(h, tp));
            context.fill(cx, cyy, cx + 3, cyy + cd.h, c(ca, barColor));
            if (h > 0.05f) {
                context.fill(cx, cyy + cd.h - 1, cx + cd.w, cyy + cd.h, c((int) (ca * h), acc));
            }

            if (er > 0.4f) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(cd.label), cx + 10, cyy + 6, 0xFFFFFFFF);
                if (cd.type == 0) {
                    context.drawTextWithShadow(this.textRenderer, Text.literal(cd.on ? "ON" : "OFF"), cx + 10, cyy + 19,
                            cd.on ? 0xFF6BFF9A : 0xFF8A93A6);
                } else if (cd.type == 1) {
                    context.drawTextWithShadow(this.textRenderer, Text.literal(cd.value), cx + 10, cyy + 19,
                            0xFF000000 | lerpRgb(acc, 0xFFFFFF, 0.55f));
                } else {
                    context.drawTextWithShadow(this.textRenderer, Text.literal(cd.value), cx + 10, cyy + 19, 0xFFC8D2E4);
                }
            }
            if (cd.type == 0) {
                int tx = cx + cd.w - 34;
                int ty = cyy + 10;
                context.fill(tx, ty, tx + 26, ty + 12, c(ca, lerpRgb(0x3B4252, 0x35D36B, tp)));
                int kx = tx + 1 + (int) (14.0f * tp);
                context.fill(kx, ty + 1, kx + 10, ty + 11, c(ca, 0xFFFFFF));
            } else if (cd.type == 1 && er > 0.4f) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(">"), cx + cd.w - 12, cyy + 12, 0xFF8FA0B8);
            }
        }

        if (ct > 0.5f) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(infoText()), px + 10, py2 + ph - 13, 0xFFAAB4C6);
        }
    }

    @Override
    public void removed() {
        Config.save();
    }
}
