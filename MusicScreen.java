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
    private static final int ACCENT = 0x3A9BFF;
    private static final int SIDE_W = 58;
    private static final int CHIP_W = 74;
    private static final String[] TABS = {"Hit", "Musik", "Radar", "Mine", "Area", "Visual", "Waypoint", "HUD"};

    private static int tab = 0;
    private static final Map<String, Float> TOGGLE = new HashMap<>();
    private static final Map<String, Float> HOVER = new HashMap<>();
    private static long lastNano = 0L;
    private static long openedAt = 0L;
    private static long tabAt = 0L;
    private static float indicatorY = 0.0f;

    private static final class Row {
        String label;
        String key;
        int type;
        boolean on;
        String value;
        int y;
    }

    private final List<Row> rows = new ArrayList<>();
    private List<String[]> hits;
    private List<String[]> songs;
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int x0;
    private int cw;
    private int rowY;
    private int infoY;

    public MusicScreen() {
        super(Text.literal("Polino Client"));
        openedAt = System.nanoTime();
        tabAt = openedAt;
        indicatorY = 0.0f;
    }

    private ButtonWidget invisible(int x, int y, int w, int h, Runnable action) {
        ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.empty(), btn -> action.run())
                .dimensions(x, y, w, h).build());
        b.setAlpha(0.0f);
        return b;
    }

    private void addRow(int type, String label, boolean on, String value, Runnable action) {
        Row r = new Row();
        r.label = label;
        r.key = tab + ":" + label;
        r.type = type;
        r.on = on;
        r.value = value;
        r.y = rowY;
        rows.add(r);
        invisible(x0 - 3, rowY, cw + 3, 18, action);
        if (type == 0 && !TOGGLE.containsKey(r.key)) {
            TOGGLE.put(r.key, on ? 1.0f : 0.0f);
        }
        rowY += 22;
        infoY = rowY + 2;
    }

    private void rowToggle(String label, boolean on, Runnable flip) {
        addRow(0, label, on, "", () -> {
            flip.run();
            Config.save();
            clearAndInit();
        });
    }

    private void rowCycle(String label, String value, Runnable next) {
        addRow(1, label, false, value, () -> {
            next.run();
            Config.save();
            clearAndInit();
        });
    }

    private void rowAction(String label, String value, Runnable action) {
        addRow(2, label, false, value, action);
    }

    @Override
    protected void init() {
        rows.clear();
        hits = Audio.hits();
        songs = Audio.songs();
        pw = Math.min(280, this.width - 8);
        ph = Math.min(196, this.height - 8);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        x0 = px + SIDE_W + 12;
        cw = pw - SIDE_W - 18;
        rowY = py + 24;
        infoY = rowY;

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            invisible(px + 4, py + 24 + i * 20, SIDE_W, 18, () -> {
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
                buildArea();
                break;
            case 5:
                buildVisual();
                break;
            case 6:
                buildWaypoint();
                break;
            default:
                buildHud();
                break;
        }
    }

    private void buildHit() {
        rowToggle("Suara Hit", Config.hitEnabled, () -> Config.hitEnabled = !Config.hitEnabled);
        rowCycle("Jenis Suara", hits.get(Math.floorMod(Config.hitIndex, hits.size()))[0],
                () -> Config.hitIndex = Math.floorMod(Config.hitIndex + 1, hits.size()));
        rowCycle("Volume", Config.hitVol + "%", () -> Config.hitVol = Config.nextVol(Config.hitVol));
        rowAction("Tes Suara", "Putar", () -> Audio.playHit());
    }

    private void buildMusic() {
        rowCycle("Lagu", songs.get(Math.floorMod(Config.songIndex, songs.size()))[0],
                () -> Config.songIndex = Math.floorMod(Config.songIndex + 1, songs.size()));
        rowAction("Lagu Sebelumnya", "<<", () -> {
            Config.songIndex = Math.floorMod(Config.songIndex - 1, songs.size());
            Config.save();
            clearAndInit();
        });
        rowToggle("Musik", Audio.isPlaying(), () -> {
            if (Audio.isPlaying()) Audio.stopSong();
            else Audio.playSong();
        });
        rowCycle("Volume", Config.songVol + "%", () -> Config.songVol = Config.nextVol(Config.songVol));
    }

    private void buildRadar() {
        rowToggle("Spawner ESP", Config.showSpawner, () -> Config.showSpawner = !Config.showSpawner);
        rowToggle("Chest ESP", Config.showChest, () -> Config.showChest = !Config.showChest);
        rowToggle("Kotak Tembus", Config.espBox, () -> Config.espBox = !Config.espBox);
        rowToggle("Garis Arah", Config.tracer, () -> Config.tracer = !Config.tracer);
        rowToggle("Daftar di HUD", Config.showList, () -> Config.showList = !Config.showList);
    }

    private void buildMine() {
        addRow(0, "Auto Mine", AutoMine.running, "", () -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (AutoMine.running) {
                AutoMine.stop(mc, "Auto Mine dihentikan");
                clearAndInit();
            } else {
                AutoMine.start(mc);
                if (AutoMine.running) this.close();
            }
        });
        rowCycle("Kecepatan", AutoMine.SPEEDS[Config.mineSpeed], () -> Config.mineSpeed = (Config.mineSpeed + 1) % 3);
        rowCycle("Gerakan", AutoMine.SMOOTHS[Config.mineSmooth], () -> Config.mineSmooth = (Config.mineSmooth + 1) % 3);
        rowToggle("Acak Manusia", Config.mineJitter, () -> Config.mineJitter = !Config.mineJitter);
        rowToggle("Auto Jalan", Config.autoWalk, () -> Config.autoWalk = !Config.autoWalk);
        rowToggle("Pickaxe 3x3", Config.pick3, () -> Config.pick3 = !Config.pick3);
        rowCycle("Target Ore", AutoMine.ORES[AutoMine.oreMode()],
                () -> Config.setInt("mine.ore", (AutoMine.oreMode() + 1) % AutoMine.ORES.length));
    }

    private void buildArea() {
        rowAction("Posisi 1", "Set", () -> setPos(1));
        rowAction("Posisi 2", "Set", () -> setPos(2));
        rowAction("Hapus Area", "Hapus", () -> {
            Config.pos1 = null;
            Config.pos2 = null;
            Config.save();
            clearAndInit();
        });
        rowToggle("Panic /spawn", Config.panic, () -> Config.panic = !Config.panic);
        rowCycle("Radius Panic", AutoMine.RADII[Config.panicRadius] + "m",
                () -> Config.panicRadius = (Config.panicRadius + 1) % AutoMine.RADII.length);
    }

    private void buildVisual() {
        Feature fb = Modules.get("fullbright");
        if (fb == null) {
            rowAction("Fullbright", "N/A", () -> {
            });
        } else {
            rowToggle("Fullbright", fb.isOn(), () -> fb.setOn(!fb.isOn(), MinecraftClient.getInstance()));
        }
        Feature fc = Modules.get("freecam");
        if (fc == null) {
            rowAction("Freecam", "N/A", () -> {
            });
        } else {
            addRow(0, "Freecam", fc.isOn(), "", () -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                fc.setOn(!fc.isOn(), mc);
                if (fc.isOn()) this.close();
                else clearAndInit();
            });
        }
        rowCycle("Speed Freecam", "x" + Config.getInt("freecam.speed", 2),
                () -> Config.setInt("freecam.speed", Config.getInt("freecam.speed", 2) % 4 + 1));
        rowToggle("Auto Sprint", Config.autoSprint, () -> Config.autoSprint = !Config.autoSprint);
    }

    private void buildWaypoint() {
        Waypoints.WP cur = Waypoints.current();
        rowToggle("Tampil Waypoint", Waypoints.show, () -> {
            Waypoints.show = !Waypoints.show;
            Waypoints.save();
        });
        rowCycle("Pilih", cur == null ? "-" : cur.name, () -> {
            if (!Waypoints.list.isEmpty()) {
                Waypoints.selected = Math.floorMod(Waypoints.selected + 1, Waypoints.list.size());
            }
        });
        rowAction("Tambah", "Di Sini", () -> {
            Waypoints.addHere(MinecraftClient.getInstance());
            clearAndInit();
        });
        rowAction("Hapus", "Terpilih", () -> {
            Waypoints.removeSelected();
            clearAndInit();
        });
        if (cur == null) {
            rowAction("Aktif", "-", () -> {
            });
        } else {
            rowToggle("Aktif", cur.on, () -> {
                cur.on = !cur.on;
                Waypoints.save();
            });
        }
    }

    private void buildHud() {
        rowAction("Edit HUD", "Buka", () -> MinecraftClient.getInstance().setScreen(new HudEditScreen(this)));
        rowAction("Reset HUD", "Reset", () -> {
            HudLayout.resetAll();
            Config.save();
            clearAndInit();
        });
        rowAction("Set Base", "Set", () -> setBase(true));
        rowAction("Hapus Base", "Hapus", () -> setBase(false));
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

    private void info(DrawContext ctx, String s, int y, int color) {
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(s), x0 + 2, y, color);
    }

    private static String fmt(BlockPos p) {
        return p == null ? "belum diset" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0.016f : Math.min(0.05f, (now - lastNano) / 1.0e9f);
        lastNano = now;

        float e = ease((now - openedAt) / 260.0e6f);
        int a = (int) (e * 255.0f);
        int oy = (int) ((1.0f - e) * 16.0f);
        int py2 = py + oy;
        float ct = clamp01((now - tabAt) / 300.0e6f);

        context.fill(px - 1, py2 - 1, px + pw + 1, py2 + ph + 1, c(a, ACCENT));
        context.fill(px, py2, px + pw, py2 + ph, c(Math.min(a, 0xF2), 0x101018));
        context.fill(px, py2, px + pw, py2 + 18, c(a, 0x0A0A12));
        context.fill(px, py2 + 18, px + pw, py2 + 19, c(a, ACCENT));
        context.fill(px, py2 + 19, px + SIDE_W + 7, py2 + ph, c(a, 0x0A0A12));

        if (e > 0.3f) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("\u00a7b\u00a7lPolino \u00a7f\u00a7lClient"), px + 8, py2 + 5, 0xFFFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("v3"), px + pw - 18, py2 + 5, 0xFF888888);
        }

        float targetInd = tab * 20.0f;
        indicatorY += (targetInd - indicatorY) * (1.0f - (float) Math.exp(-dt * 16.0f));
        int indY = py2 + 24 + (int) indicatorY;
        context.fill(px + 3, indY, px + SIDE_W + 5, indY + 18, c((int) (a * 0.30f), ACCENT));
        context.fill(px + 3, indY, px + 5, indY + 18, c(a, ACCENT));

        for (int i = 0; i < TABS.length; i++) {
            int ty = py2 + 24 + i * 20;
            boolean hv = mouseX >= px + 4 && mouseX <= px + 4 + SIDE_W && mouseY >= py + 24 + i * 20 && mouseY <= py + 24 + i * 20 + 18;
            int color = i == tab ? 0xFFFFFFFF : (hv ? 0xFFD0E4FF : 0xFF8FA0B8);
            if (e > 0.3f) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(TABS[i]), px + 12, ty + 5, color);
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float er = ease(ct * 1.5f - i * 0.12f);
            if (er <= 0.01f) continue;
            int ra = (int) (a * er);
            int xo = (int) ((1.0f - er) * 16.0f);
            int ry = r.y + oy;

            boolean hv = mouseX >= x0 - 3 && mouseX <= x0 + cw && mouseY >= r.y && mouseY <= r.y + 18;
            float h = HOVER.containsKey(r.key) ? HOVER.get(r.key) : 0.0f;
            h += ((hv ? 1.0f : 0.0f) - h) * (1.0f - (float) Math.exp(-dt * 16.0f));
            HOVER.put(r.key, h);

            context.fill(x0 - 3 + xo, ry, x0 + cw + xo, ry + 18, c(ra, lerpRgb(0x1A1F2B, 0x2A3350, h)));
            if (h > 0.05f) {
                context.fill(x0 - 3 + xo, ry, x0 - 1 + xo, ry + 18, c((int) (ra * h), ACCENT));
            }
            if (er > 0.4f) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(r.label), x0 + 5 + xo, ry + 5, 0xFFFFFFFF);
            }

            if (r.type == 0) {
                float tp = TOGGLE.containsKey(r.key) ? TOGGLE.get(r.key) : (r.on ? 1.0f : 0.0f);
                tp += ((r.on ? 1.0f : 0.0f) - tp) * (1.0f - (float) Math.exp(-dt * 14.0f));
                TOGGLE.put(r.key, tp);
                int tx = x0 + cw - 32 + xo;
                int ty = ry + 3;
                context.fill(tx, ty, tx + 26, ty + 12, c(ra, lerpRgb(0x3B4252, 0x35D36B, tp)));
                int kx = tx + 1 + (int) (14.0f * tp);
                context.fill(kx, ty + 1, kx + 10, ty + 11, c(ra, 0xFFFFFF));
            } else {
                int chx = x0 + cw - CHIP_W - 3 + xo;
                int chipBg = r.type == 2 ? lerpRgb(0x2D6FB8, ACCENT, h) : lerpRgb(0x252B3A, 0x33405E, h);
                context.fill(chx, ry + 2, chx + CHIP_W, ry + 16, c(ra, chipBg));
                if (er > 0.4f) {
                    int tw = this.textRenderer.getWidth(r.value);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(r.value),
                            chx + (CHIP_W - tw) / 2, ry + 5, r.type == 1 ? 0xFF9FD0FF : 0xFFFFFFFF);
                }
            }
        }

        if (ct > 0.6f) {
            int iy = infoY + oy;
            if (tab == 1) {
                info(context, "Status: " + (Audio.isPlaying() ? "memutar" : "berhenti"), iy, 0xFFAAAAAA);
            } else if (tab == 2) {
                info(context, Finder.spawnerCount + " spawner, " + Finder.chestCount + " chest terdeteksi", iy, 0xFFAAAAAA);
                info(context, "Merah = spawner, Oranye = chest", iy + 10, 0xFF777777);
            } else if (tab == 3) {
                info(context, "Pegang pickaxe. Area diatur di tab Area.", iy, 0xFF777777);
                info(context, "Ore: hanya yang terlihat (nempel udara)", iy + 10, 0xFF777777);
            } else if (tab == 4) {
                info(context, "P1: " + fmt(Config.pos1) + "   P2: " + fmt(Config.pos2), iy, 0xFFAAAAAA);
                info(context, "Ukuran: " + AutoMine.volume() + " blok", iy + 10, 0xFFAAAAAA);
            } else if (tab == 5) {
                info(context, "Freecam: W A S D, Spasi naik, Shift turun, Ctrl cepat", iy, 0xFF777777);
            } else if (tab == 6) {
                Waypoints.WP w = Waypoints.current();
                if (w == null) {
                    info(context, "Belum ada waypoint", iy, 0xFFAAAAAA);
                } else {
                    info(context, w.name + ": " + w.x + " " + w.y + " " + w.z, iy, 0xFFAAAAAA);
                    info(context, (Waypoints.selected + 1) + " dari " + Waypoints.list.size(), iy + 10, 0xFF777777);
                }
            } else if (tab == 7) {
                info(context, "Base: " + fmt(Config.base), iy, 0xFFAAAAAA);
                info(context, "Edit HUD: pilih elemen, geser, ON/OFF", iy + 10, 0xFF777777);
            }
        }
    }

    @Override
    public void removed() {
        Config.save();
    }
}
