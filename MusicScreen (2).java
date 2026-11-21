package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MusicScreen extends Screen {
    private static final int[] THEMES = {0x3A9BFF, 0xA06BFF, 0xFF5A5A, 0x35D36B, 0xFFA030};
    private static final String[] THEME_NAMES = {"Biru", "Ungu", "Merah", "Hijau", "Oranye"};
    private static final String[] TABS = {"Hit", "Musik", "Lagu", "Radar", "Mine", "Lurus", "Ore", "Area", "Visual", "Surv", "Titik", "HUD"};
    private static final int[] TREE_RADII = {8, 12, 16, 24};

    private static int tab = 0;
    private static final int PROWS = 7;
    private static String pQuery = "";
    private static String pLetter = "";
    private static int pPage = 0;
    private static final Map<String, Float> TOGGLE = new HashMap<>();
    private static final Map<String, Float> HOVER = new HashMap<>();
    private static final Map<String, Long> RIPPLE = new HashMap<>();
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
    private final List<Integer> pList = new ArrayList<>();
    private final List<String> pLetters = new ArrayList<>();
    private TextFieldWidget songField = null;
    private List<String[]> hits;
    private List<String[]> songs;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int tabW;
    private int colW;

    private static final int CARD_H = 36;

    public MusicScreen() {
        super(Text.literal("Polino Client"));
        openedAt = System.nanoTime();
        tabAt = openedAt;
        indicatorX = 0.0f;
    }

    static int accent() {
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
        invisible(c.x, c.y, c.w, c.h, () -> {
            RIPPLE.put(c.key, System.nanoTime());
            action.run();
        });
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
        songField = null;
        hits = Audio.hits();
        songs = Audio.songs();
        pw = Math.min(480, this.width - 8);
        ph = Math.min(300, this.height - 8);
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

        switch (TABS[tab]) {
            case "Hit":
                buildHit();
                break;
            case "Musik":
                buildMusic();
                break;
            case "Lagu":
                buildLagu();
                break;
            case "Radar":
                buildRadar();
                break;
            case "Mine":
                buildMine();
                break;
            case "Lurus":
                buildPick();
                break;
            case "Ore":
                buildOre();
                break;
            case "Area":
                buildArea();
                break;
            case "Visual":
                buildVisual();
                break;
            case "Surv":
                buildSurvival();
                break;
            case "Titik":
                buildWaypoint();
                break;
            default:
                buildHud();
                break;
        }
    }

    private boolean tabActive(int i) {
        switch (TABS[i]) {
            case "Musik":
            case "Lagu":
                return Audio.isPlaying();
            case "Mine":
                return AutoMine.running && !AutoMine.oreSearch;
            case "Ore":
                return AutoMine.running && AutoMine.oreSearch;
            case "Lurus": {
                Feature f = Modules.get("pickmine");
                return f != null && f.isOn();
            }
            case "Visual": {
                Feature a1 = Modules.get("freecam");
                Feature a2 = Modules.get("fullbright");
                return (a1 != null && a1.isOn()) || (a2 != null && a2.isOn());
            }
            case "Surv": {
                Feature f = Modules.get("autotree");
                return f != null && f.isOn();
            }
            default:
                return false;
        }
    }

    private int tabIndex(String name) {
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i].equals(name)) return i;
        }
        return 0;
    }

    private static String firstKey(String title) {
        if (title.isEmpty()) return "#";
        char ch = Character.toUpperCase(title.charAt(0));
        return Character.isLetterOrDigit(ch) ? String.valueOf(ch) : "#";
    }

    private void pBuildLetters() {
        pLetters.clear();
        pLetters.add("");
        List<String> keys = new ArrayList<>();
        for (String[] s : songs) {
            String k = firstKey(s[0]);
            if (!keys.contains(k)) keys.add(k);
        }
        java.util.Collections.sort(keys);
        pLetters.addAll(keys);
        if (!pLetters.contains(pLetter)) pLetter = "";
    }

    private int pPages() {
        return Math.max(1, (pList.size() + PROWS - 1) / PROWS);
    }

    private void pRefilter() {
        pList.clear();
        String q = pQuery.trim().toLowerCase();
        for (int i = 0; i < songs.size(); i++) {
            String title = songs.get(i)[0];
            if (!q.isEmpty() && !title.toLowerCase().contains(q)) continue;
            if (!pLetter.isEmpty() && !firstKey(title).equals(pLetter)) continue;
            pList.add(i);
        }
        pPage = Math.max(0, Math.min(pPage, pPages() - 1));
    }

    private void pPick(int row) {
        int idx = pPage * PROWS + row;
        if (idx >= pList.size()) return;
        Config.songIndex = pList.get(idx);
        Config.save();
        Audio.playSong();
    }

    private void buildLagu() {
        pBuildLetters();
        songField = new TextFieldWidget(this.textRenderer, px + 8, py + 54, pw - 16 - 86, 18, Text.literal("Cari"));
        songField.setMaxLength(40);
        songField.setText(pQuery);
        songField.setChangedListener(s -> {
            pQuery = s;
            pPage = 0;
            pRefilter();
        });
        addSelectableChild(songField);
        invisible(px + pw - 8 - 80, py + 54, 80, 18, () -> {
            int i = pLetters.indexOf(pLetter);
            pLetter = pLetters.get((i + 1) % pLetters.size());
            pPage = 0;
            pRefilter();
        });
        for (int i = 0; i < PROWS; i++) {
            final int row = i;
            invisible(px + 8, py + 78 + i * 24, pw - 16, 21, () -> pPick(row));
        }
        int ny = py + 78 + PROWS * 24 + 2;
        invisible(px + 8, ny, 44, 18, () -> pPage = Math.max(0, pPage - 1));
        invisible(px + 56, ny, 44, 18, () -> pPage = Math.min(pPages() - 1, pPage + 1));
        invisible(px + pw - 8 - 70, ny, 70, 18, () -> {
            if (!songs.isEmpty()) {
                Config.songIndex = (int) (Math.random() * songs.size());
                Config.save();
                Audio.playSong();
            }
        });
        pRefilter();
    }

    private void stopPick() {
        Feature pm = Modules.get("pickmine");
        if (pm != null && pm.isOn()) pm.setOn(false, MinecraftClient.getInstance());
    }

    private void buildPick() {
        Feature pm = Modules.get("pickmine");
        if (pm == null) {
            cardAction("Pickaxe Mining", "N/A", () -> {
            });
        } else {
            addCard(0, "Pickaxe Mining", pm.isOn(), "", () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                pm.setOn(!pm.isOn(), mc);
                if (pm.isOn()) this.close();
                else clearAndInit();
            });
        }
        cardCycle("Mode", Config.getInt("pick.mode", 0) == 1 ? "Auto" : "Manual",
                () -> Config.setInt("pick.mode", Config.getInt("pick.mode", 0) == 1 ? 0 : 1));
        cardCycle("Kecepatan", AutoMine.SPEEDS[Config.mineSpeed], () -> Config.mineSpeed = (Config.mineSpeed + 1) % 3);
        cardCycle("Kehalusan Kamera", AutoMine.SMOOTHS[Config.mineSmooth], () -> Config.mineSmooth = (Config.mineSmooth + 1) % 3);
        cardToggle("Batas Aman", Config.getBool("mine.protect", true),
                () -> Config.setBool("mine.protect", !Config.getBool("mine.protect", true)));
        cardToggle("Tampil Batas", Config.getBool("mine.showBox", true),
                () -> Config.setBool("mine.showBox", !Config.getBool("mine.showBox", true)));
    }

    private void buildHit() {
        cardToggle("Suara Hit", Config.hitEnabled, () -> Config.hitEnabled = !Config.hitEnabled);
        cardCycle("Jenis Suara", hits.get(Math.floorMod(Config.hitIndex, hits.size()))[0],
                () -> Config.hitIndex = Math.floorMod(Config.hitIndex + 1, hits.size()));
        cardCycle("Volume Hit", Config.hitVol + "%", () -> Config.hitVol = Config.nextVol(Config.hitVol));
        cardAction("Tes Suara", "Putar", () -> Audio.playHit());
    }

    private void buildMusic() {
        cardAction("Daftar Lagu", "Buka", () -> {
            tab = tabIndex("Lagu");
            tabAt = System.nanoTime();
            clearAndInit();
        });
        cardCycle("Lagu", songs.get(Math.floorMod(Config.songIndex, songs.size()))[0],
                () -> Config.songIndex = Math.floorMod(Config.songIndex + 1, songs.size()));
        cardAction("Lagu Sebelumnya", "<<", () -> {
            Config.songIndex = Math.floorMod(Config.songIndex - 1, songs.size());
            Config.save();
            clearAndInit();
        });
        cardAction("Lagu Berikutnya", ">>", () -> {
            Config.songIndex = Math.floorMod(Config.songIndex + 1, songs.size());
            Config.save();
            clearAndInit();
        });
        cardToggle("Musik", Audio.isPlaying(), () -> {
            if (Audio.isPlaying()) Audio.stopSong();
            else Audio.playSong();
        });
        cardCycle("Setelah Selesai", Audio.END_MODES[Audio.endMode()],
                () -> Config.setInt("music.mode", (Audio.endMode() + 1) % Audio.END_MODES.length));
        cardCycle("Volume Lagu", Config.songVol + "%", () -> Config.songVol = Config.nextVol(Config.songVol));
    }

    private void buildRadar() {
        cardToggle("Spawner ESP", Config.showSpawner, () -> Config.showSpawner = !Config.showSpawner);
        cardToggle("Chest ESP", Config.showChest, () -> Config.showChest = !Config.showChest);
        cardToggle("Kotak Tembus", Config.espBox, () -> Config.espBox = !Config.espBox);
        cardToggle("Garis Arah", Config.tracer, () -> Config.tracer = !Config.tracer);
        cardToggle("Daftar di HUD", Config.showList, () -> Config.showList = !Config.showList);
        cardCycle("Kalibrasi FOV", Config.getInt("esp.fovcal", 100) + "%", () -> {
            int v = Config.getInt("esp.fovcal", 100);
            Config.setInt("esp.fovcal", v >= 110 ? 90 : v + 5);
        });
        cardToggle("Bobbing Layar", MinecraftClient.getInstance().options.getBobView().getValue(), () -> {
            net.minecraft.client.option.SimpleOption<Boolean> bob = MinecraftClient.getInstance().options.getBobView();
            bob.setValue(!bob.getValue());
        });
    }

    private void buildMine() {
        addCard(0, "Auto Mine", AutoMine.running && !AutoMine.oreSearch, "", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (AutoMine.running) {
                AutoMine.stop(mc, "Auto Mine dihentikan");
                clearAndInit();
            } else {
                stopPick();
                AutoMine.start(mc, false);
                if (AutoMine.running) this.close();
            }
        });
        cardCycle("Mode Gali", Config.getInt("mine.mode", 0) == 1 ? "3x3 Depan" : "Biasa",
                () -> Config.setInt("mine.mode", Config.getInt("mine.mode", 0) == 1 ? 0 : 1));
        cardCycle("Kecepatan", AutoMine.SPEEDS[Config.mineSpeed], () -> Config.mineSpeed = (Config.mineSpeed + 1) % 3);
        cardCycle("Kehalusan Kamera", AutoMine.SMOOTHS[Config.mineSmooth], () -> Config.mineSmooth = (Config.mineSmooth + 1) % 3);
        cardCycle("Jarak Gali", (Config.getInt("mine.range10", 45) / 10.0) + " blok", () -> {
            int cur = Config.getInt("mine.range10", 45);
            Config.setInt("mine.range10", cur >= 45 ? 30 : cur + 5);
        });
        cardToggle("Batas Aman", Config.getBool("mine.protect", true),
                () -> Config.setBool("mine.protect", !Config.getBool("mine.protect", true)));
        cardToggle("Acak Manusia", Config.mineJitter, () -> Config.mineJitter = !Config.mineJitter);
        cardToggle("Auto Jalan", Config.autoWalk, () -> Config.autoWalk = !Config.autoWalk);
        cardToggle("Hammer 3x3", Config.pick3, () -> Config.pick3 = !Config.pick3);
        cardToggle("Tampil Batas", Config.getBool("mine.showBox", true),
                () -> Config.setBool("mine.showBox", !Config.getBool("mine.showBox", true)));
    }

    private void buildOre() {
        addCard(0, "Cari Ore", AutoMine.running && AutoMine.oreSearch, "", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (AutoMine.running) {
                AutoMine.stop(mc, "Cari Ore dihentikan");
                clearAndInit();
            } else {
                stopPick();
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

    private String trimTo(String s, int maxW) {
        if (this.textRenderer.getWidth(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && this.textRenderer.getWidth(t + "...") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
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
        switch (TABS[tab]) {
            case "Hit":
                return "Suara bawaan atau custom (hit.ogg di repo)";
            case "Musik":
                return songs.size() + " lagu";
            case "Lagu":
                return pList.size() + " dari " + songs.size() + " lagu. Ketuk buat memutar";
            case "Radar":
                return Finder.spawnerCount + " spawner, " + Finder.chestCount + " chest | matikan Bobbing biar pas";
            case "Mine":
                return Config.getInt("mine.mode", 0) == 1 ? "3x3: terowongan, berhenti di batas Area"
                        : "Pegang pickaxe. Area di tab Area";
            case "Lurus":
                return Config.getInt("pick.mode", 0) == 1 ? "Auto: gali lurus, maju sendiri"
                        : "Manual: tahan serang di blok yang lu tatap";
            case "Ore":
                return "Cari " + AutoMine.ORES[AutoMine.oreMode()] + (AutoMine.oreHidden() ? " (tembus batu)" : " (terlihat)");
            case "Area":
                return "P1: " + fmt(Config.pos1) + "  P2: " + fmt(Config.pos2) + "  (" + AutoMine.volume() + " blok)";
            case "Visual":
                return "Freecam: WASD, Spasi naik, Shift turun, Ctrl cepat";
            case "Surv":
                return "Kapak kanan, sapling kiri";
            case "Titik": {
                Waypoints.WP w = Waypoints.current();
                if (w == null) return "Belum ada titik";
                return w.name + ": " + w.x + " " + w.y + " " + w.z + "  (" + (Waypoints.selected + 1) + "/" + Waypoints.list.size() + ")";
            }
            default:
                return "Base: " + fmt(Config.base);
        }
    }

    private void chip(DrawContext ctx, int x, int y, int w, int h, String text, boolean hover, int acc, int alpha) {
        ctx.fill(x, y, x + w, y + h, c(alpha, hover ? 0x2F3A55 : 0x252B3A));
        int tw = this.textRenderer.getWidth(text);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(text), x + (w - tw) / 2, y + (h - 8) / 2, 0xFF000000 | lerpRgb(acc, 0xFFFFFF, 0.5f));
    }

    private void renderLagu(DrawContext ctx, int mouseX, int mouseY, float dt, int a, int oy, float ct, int acc) {
        if (oy == 0) songField.render(ctx, mouseX, mouseY, 0.0f);
        if (songField.getText().isEmpty() && oy == 0) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("ketik judul..."), px + 14, py + 59, 0xFF666E80);
        }
        boolean hvL = mouseX >= px + pw - 88 && mouseX <= px + pw - 8 && mouseY >= py + 54 && mouseY <= py + 72;
        chip(ctx, px + pw - 8 - 80, py + 54 + oy, 80, 18, "Huruf: " + (pLetter.isEmpty() ? "Semua" : pLetter), hvL, acc, a);

        int curIdx = Math.floorMod(Config.songIndex, Math.max(1, songs.size()));
        for (int i = 0; i < PROWS; i++) {
            int idx = pPage * PROWS + i;
            if (idx >= pList.size()) continue;
            int songIdx = pList.get(idx);
            float er = ease(ct * 1.6f - i * 0.09f);
            if (er <= 0.01f) continue;
            int ra = (int) (a * er);
            int xo = (int) ((1.0f - er) * 14.0f);
            int ry = py + 78 + i * 24;
            boolean hv = mouseX >= px + 8 && mouseX <= px + pw - 8 && mouseY >= ry && mouseY <= ry + 21;
            String hk = "lg:" + i;
            float h = HOVER.containsKey(hk) ? HOVER.get(hk) : 0.0f;
            h += ((hv ? 1.0f : 0.0f) - h) * (1.0f - (float) Math.exp(-dt * 16.0f));
            HOVER.put(hk, h);
            boolean playing = Audio.isPlaying() && songIdx == curIdx;
            int bg = playing ? lerpRgb(0x181D29, acc, 0.38f) : lerpRgb(0x181D29, 0x222A3D, h);
            int ryy = ry + oy;
            ctx.fill(px + 8 + xo + 1, ryy + 2, px + pw - 8 + xo + 1, ryy + 23, c((int) (ra * 0.35f), 0x000000));
            ctx.fill(px + 8 + xo, ryy, px + pw - 8 + xo, ryy + 21, c(ra, bg));
            ctx.fill(px + 8 + xo, ryy, px + 11 + xo, ryy + 21, c(ra, lerpRgb(0x2A3350, acc, Math.max(h, playing ? 1.0f : 0.0f))));
            if (er > 0.4f) {
                ctx.drawTextWithShadow(this.textRenderer, Text.literal((songIdx + 1) + "."), px + 16 + xo, ryy + 7, 0xFF8FA0B8);
                ctx.drawTextWithShadow(this.textRenderer, Text.literal(trimTo(songs.get(songIdx)[0], pw - 16 - 70)),
                        px + 42 + xo, ryy + 7, playing ? 0xFF6BFF9A : 0xFFFFFFFF);
                if (playing) {
                    ctx.drawTextWithShadow(this.textRenderer, Text.literal("memutar"), px + pw - 62 + xo, ryy + 7, 0xFF6BFF9A);
                }
            }
        }
        if (pList.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("Tidak ada lagu yang cocok"), px + 16, py + 90 + oy, 0xFF8FA0B8);
        }
        int ny = py + 78 + PROWS * 24 + 2;
        chip(ctx, px + 8, ny + oy, 44, 18, "<", mouseX >= px + 8 && mouseX <= px + 52 && mouseY >= ny && mouseY <= ny + 18, acc, a);
        chip(ctx, px + 56, ny + oy, 44, 18, ">", mouseX >= px + 56 && mouseX <= px + 100 && mouseY >= ny && mouseY <= ny + 18, acc, a);
        chip(ctx, px + pw - 8 - 70, ny + oy, 70, 18, "Acak", mouseX >= px + pw - 78 && mouseX <= px + pw - 8 && mouseY >= ny && mouseY <= ny + 18, acc, a);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Hal " + (pPage + 1) + "/" + pPages()), px + 108, ny + 5 + oy, 0xFFAAB4C6);
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

        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 4.0e8);
        context.fill(px - 4, py2 - 4, px + pw + 4, py2 + ph + 4, c((int) (a * 0.07f * (0.6f + 0.4f * pulse)), acc));
        context.fill(px - 3, py2 - 3, px + pw + 3, py2 + ph + 3, c((int) (a * 0.10f * (0.6f + 0.4f * pulse)), acc));
        context.fill(px - 2, py2 - 2, px + pw + 2, py2 + ph + 2, c((int) (a * 0.18f), acc));
        context.fill(px - 1, py2 - 1, px + pw + 1, py2 + ph + 1, c(a, acc));
        for (int gy = 0; gy < ph; gy += 2) {
            float gt = gy / (float) ph;
            context.fill(px, py2 + gy, px + pw, py2 + Math.min(ph, gy + 2), c(Math.min(a, 0xF6), lerpRgb(0x131A2E, 0x09090F, gt)));
        }
        for (int i = 0; i < 16; i++) {
            double seed = Math.abs(Math.sin(i * 12.9898)) ;
            int bx = px + 12 + (int) (seed * (pw - 24));
            double tt = (now / 1.0e9) * (5 + (i % 5) * 2) + i * 37.0;
            int by = py2 + 52 + (int) (tt % Math.max(10, ph - 90));
            int wx = bx + (int) (Math.sin(now / 1.6e9 + i) * 7.0);
            context.fill(wx, by, wx + 2, by + 2, c((int) (a * 0.10f), 0xFFFFFF));
        }
        context.fill(px, py2, px + pw, py2 + 26, c(a, 0x08080E));
        context.fill(px, py2 + 26, px + pw, py2 + 27, c(a, lerpRgb(0x0E0E16, acc, 0.6f)));
        context.fill(px, py2 + 27, px + pw, py2 + 49, c(a, 0x0A0A12));
        context.fill(px, py2 + ph - 22, px + pw, py2 + ph, c(a, 0x08080E));
        context.fill(px, py2 + ph - 22, px + pw, py2 + ph - 21, c(a, lerpRgb(0x0E0E16, acc, 0.35f)));

        if (e > 0.3f) {
            int tcol = 0xFF000000 | lerpRgb(acc, 0xFFFFFF, 0.25f + 0.35f * pulse);
            context.drawTextWithShadow(this.textRenderer, Text.literal("\u00a7lPolino"), px + 10, py2 + 9, tcol);
            context.drawTextWithShadow(this.textRenderer, Text.literal("\u00a7lClient"),
                    px + 10 + this.textRenderer.getWidth("\u00a7lPolino "), py2 + 9, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("v5"), px + pw - 18, py2 + 9, 0xFF888888);
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
                if (tabActive(i)) {
                    context.fill(tx + tabW - 7, py2 + 31, tx + tabW - 4, py2 + 34, c(a, 0x6BFF9A));
                }
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
            context.fill(cx + 1, cyy + 2, cx + cd.w + 1, cyy + cd.h + 2, c((int) (ca * 0.35f), 0x000000));
            int hx = (int) (h * 2.0f);
            context.fill(cx - hx, cyy - hx, cx + cd.w + hx, cyy + cd.h + hx, c(ca, bg));
            int barColor = lerpRgb(0x2A3350, acc, Math.max(h, tp));
            context.fill(cx, cyy, cx + 3, cyy + cd.h, c(ca, barColor));
            if (h > 0.05f) {
                int sx = cx + (int) (((now / 1.0e9) * 70.0) % (cd.w + 40)) - 20;
                int s1 = Math.max(cx, sx);
                int s2 = Math.min(cx + cd.w, sx + 14);
                if (s2 > s1) {
                    context.fill(s1, cyy, s2, cyy + cd.h, c((int) (ca * 0.08f * h), 0xFFFFFF));
                }
            }
            Long rt = RIPPLE.get(cd.key);
            if (rt != null) {
                float rp = (now - rt) / 320.0e6f;
                if (rp < 1.0f) {
                    int inset = (int) ((1.0f - rp) * (cd.w / 2));
                    context.fill(cx + inset, cyy, cx + cd.w - inset, cyy + cd.h, c((int) (ca * 0.28f * (1.0f - rp)), 0xFFFFFF));
                }
            }
            if (h > 0.05f) {
                context.fill(cx, cyy + cd.h - 1, cx + cd.w, cyy + cd.h, c((int) (ca * h), acc));
            }

            if (er > 0.4f) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(cd.label), cx + 10, cyy + 7, 0xFFFFFFFF);
                if (cd.type == 0) {
                    context.drawTextWithShadow(this.textRenderer, Text.literal(cd.on ? "ON" : "OFF"), cx + 10, cyy + 21,
                            cd.on ? 0xFF6BFF9A : 0xFF8A93A6);
                } else if (cd.type == 1) {
                    context.drawTextWithShadow(this.textRenderer, Text.literal(trimTo(cd.value, cd.w - 28)), cx + 10, cyy + 21,
                            0xFF000000 | lerpRgb(acc, 0xFFFFFF, 0.55f));
                } else {
                    context.drawTextWithShadow(this.textRenderer, Text.literal(trimTo(cd.value, cd.w - 28)), cx + 10, cyy + 21, 0xFFC8D2E4);
                }
            }
            if (cd.type == 0) {
                int tx = cx + cd.w - 34;
                int ty = cyy + 12;
                context.fill(tx, ty, tx + 26, ty + 12, c(ca, lerpRgb(0x3B4252, 0x35D36B, tp)));
                int kx = tx + 1 + (int) (14.0f * tp);
                context.fill(kx, ty + 1, kx + 10, ty + 11, c(ca, 0xFFFFFF));
            } else if (cd.type == 1 && er > 0.4f) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(">"), cx + cd.w - 12, cyy + 14, 0xFF8FA0B8);
            }
        }

        if (TABS[tab].equals("Lagu") && songField != null) {
            renderLagu(context, mouseX, mouseY, dt, a, oy, ct, acc);
        }

        if (e > 0.5f) {
            boolean playing = Audio.isPlaying();
            int barBase = py2 + ph - 6;
            for (int i = 0; i < 4; i++) {
                int bh = playing ? 2 + (int) ((Math.sin(now / 1.2e8 * (1.0 + i * 0.37) + i * 1.9) + 1.0) * 3.5) : 2;
                context.fill(px + 10 + i * 4, barBase - bh, px + 13 + i * 4, barBase, c(a, playing ? acc : 0x3B4252));
            }
            int half = pw / 2;
            String left = playing ? Audio.currentTitle : "Tidak memutar";
            context.drawTextWithShadow(this.textRenderer, Text.literal(trimTo(left, half - 40)), px + 30, py2 + ph - 15,
                    playing ? 0xFF6BFF9A : 0xFF8A93A6);
            String right = trimTo(infoText(), pw - half - 20);
            context.drawTextWithShadow(this.textRenderer, Text.literal(right),
                    px + pw - 10 - this.textRenderer.getWidth(right), py2 + ph - 15, 0xFFAAB4C6);
        }
    }

    @Override
    public void removed() {
        Config.save();
    }
}
