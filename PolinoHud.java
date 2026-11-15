package com.hitmusic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class PolinoHud {
    private static double camX;
    private static double camY;
    private static double camZ;
    private static double fwdX;
    private static double fwdY;
    private static double fwdZ;
    private static double rightX;
    private static double rightZ;
    private static double upX;
    private static double upY;
    private static double upZ;
    private static double focal;
    private static int scrW;
    private static int scrH;

    private static int frames = 0;
    private static int fps = 0;
    private static long lastNano = 0L;

    private PolinoHud() {}

    private static void setup(MinecraftClient mc, ClientPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        double yawR = Math.toRadians(p.getYaw());
        double pitchR = Math.toRadians(p.getPitch());
        double sinYaw = Math.sin(yawR);
        double cosYaw = Math.cos(yawR);
        double sinPitch = Math.sin(pitchR);
        double cosPitch = Math.cos(pitchR);
        fwdX = -sinYaw * cosPitch;
        fwdY = -sinPitch;
        fwdZ = cosYaw * cosPitch;
        rightX = -cosYaw;
        rightZ = -sinYaw;
        upX = -sinYaw * sinPitch;
        upY = cosPitch;
        upZ = cosYaw * sinPitch;
        scrW = mc.getWindow().getScaledWidth();
        scrH = mc.getWindow().getScaledHeight();
        double fov = Math.toRadians(mc.options.getFov().getValue());
        focal = (scrH / 2.0) / Math.tan(fov / 2.0);
    }

    private static boolean project(double wx, double wy, double wz, double[] out) {
        double dx = wx - camX;
        double dy = wy - camY;
        double dz = wz - camZ;
        double z = dx * fwdX + dy * fwdY + dz * fwdZ;
        if (z < 0.1) return false;
        double x = dx * rightX + dz * rightZ;
        double y = dx * upX + dy * upY + dz * upZ;
        out[0] = scrW / 2.0 + x / z * focal;
        out[1] = scrH / 2.0 - y / z * focal;
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
            if (x < -2 || y < -2 || x > scrW + 2 || y > scrH + 2) continue;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void drawEsp(DrawContext ctx, MinecraftClient mc) {
        if (PolinoFinder.results.isEmpty()) return;
        if (!PolinoConfig.espBox && !PolinoConfig.tracer) return;
        double[] out = new double[3];
        int tracers = 0;
        for (PolinoFinder.Entry e : PolinoFinder.results) {
            int color = e.spawner ? 0xFFFF5555 : 0xFFFFAA00;
            double dxc = e.pos.getX() + 0.5 - camX;
            double dyc = e.pos.getY() + 0.5 - camY;
            double dzc = e.pos.getZ() + 0.5 - camZ;
            long dist = Math.round(Math.sqrt(dxc * dxc + dyc * dyc + dzc * dzc));

            if (PolinoConfig.tracer && tracers < 8) {
                if (project(e.pos.getX() + 0.5, e.pos.getY() + 0.5, e.pos.getZ() + 0.5, out)) {
                    double tx = Math.max(-4000, Math.min(4000, out[0]));
                    double ty = Math.max(-4000, Math.min(4000, out[1]));
                    line(ctx, scrW / 2, scrH, (int) tx, (int) ty, color);
                    tracers++;
                }
            }

            if (PolinoConfig.espBox) {
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

        frames++;
        long now = System.nanoTime();
        if (now - lastNano >= 1000000000L) {
            fps = frames;
            frames = 0;
            lastNano = now;
        }

        setup(mc, p);
        drawEsp(ctx, mc);

        int y = 4;
        if (PolinoConfig.watermark) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("\u00a7b\u00a7lPolino \u00a7f\u00a7lClient"), 4, y, 0xFFFFFFFF);
            y += 11;
        }
        if (PolinoConfig.showCoords) {
            double rad = Math.toRadians(p.getYaw());
            String face = PolinoFinder.compass(-Math.sin(rad), Math.cos(rad));
            String s = "XYZ: " + p.getBlockPos().getX() + " " + p.getBlockPos().getY() + " " + p.getBlockPos().getZ() + "  " + face;
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), 4, y, 0xFFFFFFFF);
            y += 10;
        }
        if (PolinoConfig.showFps) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(fps + " FPS"), 4, y, 0xFFFFFFFF);
            y += 10;
        }
        if (PolinoConfig.base != null) {
            double bx = PolinoConfig.base.getX() + 0.5 - p.getX();
            double by = PolinoConfig.base.getY() + 0.5 - p.getY();
            double bz = PolinoConfig.base.getZ() + 0.5 - p.getZ();
            long d = Math.round(Math.sqrt(bx * bx + by * by + bz * bz));
            String s = "Base: " + d + "m " + PolinoFinder.compass(bx, bz);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), 4, y, 0xFF55FFFF);
            y += 10;
        }
        if (PolinoMine.running) {
            ctx.drawTextWithShadow(mc.textRenderer,
                    Text.literal("Auto Mine: " + PolinoMine.remaining + " blok tersisa"), 4, y, 0xFF55FF55);
            y += 10;
        }
        if (PolinoConfig.showList) {
            int n = 0;
            for (PolinoFinder.Entry e : PolinoFinder.results) {
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
