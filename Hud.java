package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class Hud {
    private static double ex;
    private static double ey;
    private static double ez;
    private static double fx;
    private static double fy;
    private static double fz;
    private static double rx;
    private static double rz;
    private static double ux;
    private static double uy;
    private static double uz;
    private static double focal;
    private static int sw;
    private static int sh;

    private Hud() {}

    private static void setup(MinecraftClient mc, ClientPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        ex = eye.x;
        ey = eye.y;
        ez = eye.z;
        double yawR = Math.toRadians(p.getYaw());
        double pitchR = Math.toRadians(p.getPitch());
        double sinYaw = Math.sin(yawR);
        double cosYaw = Math.cos(yawR);
        double sinPitch = Math.sin(pitchR);
        double cosPitch = Math.cos(pitchR);
        fx = -sinYaw * cosPitch;
        fy = -sinPitch;
        fz = cosYaw * cosPitch;
        rx = -cosYaw;
        rz = -sinYaw;
        ux = -sinYaw * sinPitch;
        uy = cosPitch;
        uz = cosYaw * sinPitch;
        sw = mc.getWindow().getScaledWidth();
        sh = mc.getWindow().getScaledHeight();
        double fov = Math.toRadians(mc.options.getFov().getValue());
        focal = (sh / 2.0) / Math.tan(fov / 2.0);
    }

    private static boolean project(double wx, double wy, double wz, double[] out) {
        double dx = wx - ex;
        double dy = wy - ey;
        double dz = wz - ez;
        double z = dx * fx + dy * fy + dz * fz;
        if (z < 0.1) return false;
        double x = dx * rx + dz * rz;
        double y = dx * ux + dy * uy + dz * uz;
        out[0] = sw / 2.0 + x / z * focal;
        out[1] = sh / 2.0 - y / z * focal;
        out[2] = z;
        return true;
    }

    private static void box(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1, y1, x2 + 1, y1 + 1, color);
        ctx.fill(x1, y2, x2 + 1, y2 + 1, color);
        ctx.fill(x1, y1, x1 + 1, y2 + 1, color);
        ctx.fill(x2, y1, x2 + 1, y2 + 1, color);
    }

    private static void line(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i += 3) {
            int x = x0 + (int) ((long) dx * i / steps);
            int y = y0 + (int) ((long) dy * i / steps);
            if (x < -2 || y < -2 || x > sw + 2 || y > sh + 2) continue;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void drawEsp(DrawContext ctx, MinecraftClient mc) {
        if (Finder.results.isEmpty()) return;
        if (!Config.espBox && !Config.tracer) return;
        double[] out = new double[3];
        int tracers = 0;
        for (Finder.Entry e : Finder.results) {
            int color = e.spawner ? 0xFFFF5555 : 0xFFFFAA00;
            double cx = e.pos.getX() + 0.5 - ex;
            double cy = e.pos.getY() + 0.5 - ey;
            double cz = e.pos.getZ() + 0.5 - ez;
            long dist = Math.round(Math.sqrt(cx * cx + cy * cy + cz * cz));

            if (Config.tracer && tracers < 8) {
                if (project(e.pos.getX() + 0.5, e.pos.getY() + 0.5, e.pos.getZ() + 0.5, out)) {
                    double tx = Math.max(-4000, Math.min(4000, out[0]));
                    double ty = Math.max(-4000, Math.min(4000, out[1]));
                    line(ctx, sw / 2, sh, (int) tx, (int) ty, color);
                    tracers++;
                }
            }

            if (Config.espBox) {
                double minX = 1e9;
                double minY = 1e9;
                double maxX = -1e9;
                double maxY = -1e9;
                boolean ok = true;
                for (int i = 0; i < 8; i++) {
                    if (!project(e.pos.getX() + (i & 1), e.pos.getY() + ((i >> 1) & 1), e.pos.getZ() + ((i >> 2) & 1), out)) {
                        ok = false;
                        break;
                    }
                    minX = Math.min(minX, out[0]);
                    maxX = Math.max(maxX, out[0]);
                    minY = Math.min(minY, out[1]);
                    maxY = Math.max(maxY, out[1]);
                }
                if (!ok) continue;
                int x1 = (int) Math.max(-4000, minX);
                int y1 = (int) Math.max(-4000, minY);
                int x2 = Math.max((int) Math.min(4000, maxX), x1 + 4);
                int y2 = Math.max((int) Math.min(4000, maxY), y1 + 4);
                box(ctx, x1, y1, x2, y2, color);
                String label = (e.spawner ? "Spawner " : "Chest ") + dist + "m";
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(label), x1, y1 - 10, color);
            }
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden) return;
        setup(mc, p);
        drawEsp(ctx, mc);

        int y = 4;
        if (Config.watermark) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("\u00a7b\u00a7lPolino \u00a7f\u00a7lClient"), 4, y, 0xFFFFFFFF);
            y += 11;
        }
        if (Config.showCoords) {
            double rad = Math.toRadians(p.getYaw());
            String face = Finder.compass(-Math.sin(rad), Math.cos(rad));
            String s = "XYZ: " + p.getBlockPos().getX() + " " + p.getBlockPos().getY() + " " + p.getBlockPos().getZ() + "  " + face;
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), 4, y, 0xFFFFFFFF);
            y += 10;
        }
        if (Config.showFps) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(mc.getCurrentFps() + " FPS"), 4, y, 0xFFFFFFFF);
            y += 10;
        }
        if (Config.base != null) {
            double bx = Config.base.getX() + 0.5 - p.getX();
            double by = Config.base.getY() + 0.5 - p.getY();
            double bz = Config.base.getZ() + 0.5 - p.getZ();
            long d = Math.round(Math.sqrt(bx * bx + by * by + bz * bz));
            String s = "Base: " + d + "m " + Finder.compass(bx, bz);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), 4, y, 0xFF55FFFF);
            y += 10;
        }
        if (AutoMine.running) {
            ctx.drawTextWithShadow(mc.textRenderer,
                    Text.literal("Auto Mine: " + AutoMine.remaining + " blok tersisa"), 4, y, 0xFF55FF55);
            y += 10;
        }
        if (Config.showList) {
            int n = 0;
            for (Finder.Entry e : Finder.results) {
                if (n++ >= 6) break;
                String s = (e.spawner ? "Spawner " : "Chest ")
                        + e.pos.getX() + " " + e.pos.getY() + " " + e.pos.getZ()
                        + "  " + Math.round(e.dist) + "m " + e.dir;
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), 4, y, e.spawner ? 0xFFFF5555 : 0xFFFFAA00);
                y += 10;
            }
        }
    }
}
