package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class HudLayout {
    public static final class El {
        public final String id;
        public final String name;
        public final boolean defOn;
        public int w = 60;
        public int h = 10;

        El(String id, String name, boolean defOn) {
            this.id = id;
            this.name = name;
            this.defOn = defOn;
        }
    }

    public static final List<El> ELS = new ArrayList<>();

    static {
        ELS.add(new El("wm", "Watermark", true));
        ELS.add(new El("xyz", "Koordinat", true));
        ELS.add(new El("fps", "FPS", false));
        ELS.add(new El("clk", "Jam", false));
        ELS.add(new El("base", "Base", true));
        ELS.add(new El("mine", "Status Mine", true));
        ELS.add(new El("radar", "Daftar Radar", true));
        ELS.add(new El("keys", "Keystrokes", false));
        ELS.add(new El("armor", "Armor", false));
    }

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private HudLayout() {}

    public static boolean on(El e) {
        return Config.getBool("hud." + e.id + ".on", e.defOn);
    }

    public static void setOn(El e, boolean v) {
        Config.setBool("hud." + e.id + ".on", v);
    }

    private static int defX(El e, int sw, int sh) {
        if (e.id.equals("keys")) return 6;
        if (e.id.equals("armor")) return Math.max(0, sw - 80);
        return 4;
    }

    private static int defY(El e, int sw, int sh) {
        switch (e.id) {
            case "wm":
                return 4;
            case "xyz":
                return 16;
            case "fps":
                return 27;
            case "clk":
                return 38;
            case "base":
                return 49;
            case "mine":
                return 60;
            case "radar":
                return 72;
            case "keys":
                return Math.max(0, sh - 62);
            case "armor":
                return Math.max(0, sh - 44);
            default:
                return 4;
        }
    }

    public static int x(El e, int sw, int sh) {
        return Config.getInt("hud." + e.id + ".x", defX(e, sw, sh));
    }

    public static int y(El e, int sw, int sh) {
        return Config.getInt("hud." + e.id + ".y", defY(e, sw, sh));
    }

    public static void move(El e, int dx, int dy, int sw, int sh) {
        int nx = Math.max(0, Math.min(Math.max(0, sw - e.w), x(e, sw, sh) + dx));
        int ny = Math.max(0, Math.min(Math.max(0, sh - e.h), y(e, sw, sh) + dy));
        Config.setInt("hud." + e.id + ".x", nx);
        Config.setInt("hud." + e.id + ".y", ny);
    }

    public static void resetAll() {
        for (El e : ELS) {
            Config.remove("hud." + e.id + ".x");
            Config.remove("hud." + e.id + ".y");
            Config.remove("hud." + e.id + ".on");
        }
    }

    private static int text(DrawContext ctx, MinecraftClient mc, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), x, y, color);
        return mc.textRenderer.getWidth(s);
    }

    private static void keyBox(DrawContext ctx, MinecraftClient mc, String label, boolean pressed, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, pressed ? 0xC03A9BFF : 0x80000000);
        if (!label.isEmpty()) {
            int tw = mc.textRenderer.getWidth(label);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(label), x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        }
    }

    private static void draw(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p, El e, int x, int y, boolean editing) {
        boolean dim = editing && !on(e);
        int white = dim ? 0xFF777777 : 0xFFFFFFFF;
        switch (e.id) {
            case "wm": {
                int w = text(ctx, mc, "\u00a7b\u00a7lPolino \u00a7f\u00a7lClient", x, y, white);
                e.w = w;
                e.h = 10;
                break;
            }
            case "xyz": {
                double rad = Math.toRadians(p.getYaw());
                String face = Finder.compass(-Math.sin(rad), Math.cos(rad));
                String s = "XYZ: " + p.getBlockPos().getX() + " " + p.getBlockPos().getY() + " " + p.getBlockPos().getZ() + "  " + face;
                e.w = text(ctx, mc, s, x, y, white);
                e.h = 10;
                break;
            }
            case "fps": {
                e.w = text(ctx, mc, Hud.fps + " FPS", x, y, white);
                e.h = 10;
                break;
            }
            case "clk": {
                e.w = text(ctx, mc, LocalTime.now().format(CLOCK), x, y, white);
                e.h = 10;
                break;
            }
            case "base": {
                if (Config.base != null) {
                    double bx = Config.base.getX() + 0.5 - p.getX();
                    double by = Config.base.getY() + 0.5 - p.getY();
                    double bz = Config.base.getZ() + 0.5 - p.getZ();
                    long d = Math.round(Math.sqrt(bx * bx + by * by + bz * bz));
                    e.w = text(ctx, mc, "Base: " + d + "m " + Finder.compass(bx, bz), x, y, dim ? 0xFF777777 : 0xFF55FFFF);
                    e.h = 10;
                } else if (editing) {
                    e.w = text(ctx, mc, "Base: belum diset", x, y, 0xFF777777);
                    e.h = 10;
                }
                break;
            }
            case "mine": {
                if (AutoMine.running) {
                    e.w = text(ctx, mc, "Auto Mine: " + AutoMine.remaining + " blok tersisa", x, y, 0xFF55FF55);
                    e.h = 10;
                } else if (editing) {
                    e.w = text(ctx, mc, "Auto Mine: nonaktif", x, y, 0xFF777777);
                    e.h = 10;
                }
                break;
            }
            case "radar": {
                int n = 0;
                int yy = y;
                int maxW = 0;
                for (Finder.Entry en : Finder.results) {
                    if (n++ >= 6) break;
                    String s = (en.spawner ? "Spawner " : "Chest ")
                            + en.pos.getX() + " " + en.pos.getY() + " " + en.pos.getZ()
                            + "  " + Math.round(en.dist) + "m " + en.dir;
                    int w = text(ctx, mc, s, x, yy, dim ? 0xFF777777 : (en.spawner ? 0xFFFF5555 : 0xFFFFAA00));
                    maxW = Math.max(maxW, w);
                    yy += 10;
                }
                if (n == 0 && editing) {
                    maxW = text(ctx, mc, "Radar: kosong", x, y, 0xFF777777);
                    yy += 10;
                }
                e.w = Math.max(maxW, 20);
                e.h = Math.max(yy - y, 10);
                break;
            }
            case "keys": {
                keyBox(ctx, mc, "W", mc.options.forwardKey.isPressed(), x + 18, y, 16, 16);
                keyBox(ctx, mc, "A", mc.options.leftKey.isPressed(), x, y + 18, 16, 16);
                keyBox(ctx, mc, "S", mc.options.backKey.isPressed(), x + 18, y + 18, 16, 16);
                keyBox(ctx, mc, "D", mc.options.rightKey.isPressed(), x + 36, y + 18, 16, 16);
                keyBox(ctx, mc, "", mc.options.jumpKey.isPressed(), x, y + 36, 52, 8);
                e.w = 52;
                e.h = 44;
                break;
            }
            case "armor": {
                EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                for (int i = 0; i < 4; i++) {
                    ItemStack st = p.getEquippedStack(slots[i]);
                    int ix = x + i * 20;
                    if (st.isEmpty()) {
                        if (editing) ctx.fill(ix, y, ix + 16, y + 16, 0x60FFFFFF);
                        continue;
                    }
                    ctx.drawItem(st, ix, y);
                    int max = st.getMaxDamage();
                    if (max > 0) {
                        int pct = (max - st.getDamage()) * 100 / max;
                        String s = pct + "%";
                        int tw = mc.textRenderer.getWidth(s);
                        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), ix + (16 - tw) / 2, y + 17, 0xFFFFFFFF);
                    }
                }
                e.w = 80;
                e.h = 27;
                break;
            }
            default:
                break;
        }
    }

    public static void drawAll(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p, boolean editing) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        for (El e : ELS) {
            if (!editing && !on(e)) continue;
            draw(ctx, mc, p, e, x(e, sw, sh), y(e, sw, sh), editing);
        }
    }
}
